'use strict';

const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { Readable } = require('node:stream');

// Carga .env simple, sin dependencia externa. Las variables del proceso tienen prioridad.
function loadEnvFile(file) {
  try {
    for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
      const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
      if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^(["'])(.*)\1$/, '$2');
    }
  } catch (_) { /* optional */ }
}
loadEnvFile(path.join(__dirname, '.env'));

const PORT = Number(process.env.PORT || 8787);
const NODE_ENV = process.env.NODE_ENV || 'development';
const AZURA_BASE = String(process.env.AZURACAST_BASE_URL || '').replace(/\/+$/, '');
const STATION_ID = String(process.env.AZURACAST_STATION_ID || '').trim();
const AZURA_KEY = String(process.env.AZURACAST_API_KEY || '').trim();
const AZURA_UPLOAD_ENABLED = String(process.env.AZURACAST_UPLOAD_ENABLED || 'false').trim().toLowerCase() === 'true';
const PUBLIC_BASE = String(process.env.PUBLIC_API_BASE_URL || '').replace(/\/+$/, '');
const DATA_DIR = path.resolve(process.env.DATA_DIR || path.join(__dirname, 'data'));
const USERS_FILE = path.join(DATA_DIR, 'users.json');
const JWT_SECRET = String(process.env.JWT_SECRET || '');
const ADMIN_EMAIL = String(process.env.BOOTSTRAP_ADMIN_EMAIL || '').trim().toLowerCase();
const ADMIN_PASSWORD = String(process.env.BOOTSTRAP_ADMIN_PASSWORD || '');
const TOKEN_LIFETIME_SECONDS = 12 * 60 * 60;
const MEDIA_TICKET_SECONDS = 4 * 60 * 60;
const MAX_BODY = 1024 * 1024;

if (JWT_SECRET.length < 40 || /REEMPLAZAR|CAMBIAR|PLACEHOLDER/i.test(JWT_SECRET)) {
  console.error('ERROR: configura JWT_SECRET con al menos 40 caracteres aleatorios en backend/.env.');
  process.exit(1);
}
if (NODE_ENV === 'production' && (!PUBLIC_BASE.startsWith('https://') || /TU-DOMINIO|CAMBIA-ESTO|PLACEHOLDER/i.test(PUBLIC_BASE))) {
  console.error('ERROR: configura PUBLIC_API_BASE_URL con la URL HTTPS pública real del gateway.');
  process.exit(1);
}
if (NODE_ENV === 'production' && ADMIN_PASSWORD && /CAMBIAR|PLACEHOLDER/i.test(ADMIN_PASSWORD)) {
  console.error('ERROR: reemplaza BOOTSTRAP_ADMIN_PASSWORD por una contraseña propia antes de iniciar.');
  process.exit(1);
}

let users = [];
let writeQueue = Promise.resolve();
const loginAttempts = new Map();

function json(res, status, body, headers = {}) {
  const data = Buffer.from(JSON.stringify(body));
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': String(data.length),
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type, Range',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    ...headers,
  });
  res.end(data);
}

function base64url(input) { return Buffer.from(input).toString('base64url'); }
function signToken(payload, seconds) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = base64url(JSON.stringify({ iss: 'afrogo-api', iat: now, exp: now + seconds, ...payload }));
  const content = `${header}.${body}`;
  return `${content}.${crypto.createHmac('sha256', JWT_SECRET).update(content).digest('base64url')}`;
}
function verifyToken(token) {
  if (!token || typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const content = `${parts[0]}.${parts[1]}`;
  const expected = crypto.createHmac('sha256', JWT_SECRET).update(content).digest();
  let received;
  try { received = Buffer.from(parts[2], 'base64url'); } catch (_) { return null; }
  if (received.length !== expected.length || !crypto.timingSafeEqual(received, expected)) return null;
  try {
    const p = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    if (p.iss !== 'afrogo-api' || !p.exp || p.exp <= Math.floor(Date.now() / 1000)) return null;
    return p;
  } catch (_) { return null; }
}
function bearer(req) {
  const m = String(req.headers.authorization || '').match(/^Bearer\s+(.+)$/i);
  return m ? m[1] : '';
}
function requireHttps(req) {
  if (NODE_ENV !== 'production') return true;
  return Boolean(req.socket.encrypted || String(req.headers['x-forwarded-proto'] || '').split(',')[0].trim() === 'https');
}
function authUser(req) {
  const payload = verifyToken(bearer(req));
  if (!payload || payload.kind !== 'user' || !payload.sub) return null;
  const user = users.find(u => u.id === payload.sub && u.status === 'approved');
  if (!user) return null;
  return user;
}
function adminUser(req) {
  const u = authUser(req);
  return u && u.role === 'admin' ? u : null;
}
function safeUser(user) {
  return { id: user.id, name: user.name, email: user.email, role: user.role, status: user.status, createdAt: user.createdAt };
}
function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
  return new Promise((resolve, reject) => crypto.scrypt(password, salt, 64, (err, derived) => {
    if (err) reject(err); else resolve({ salt, hash: derived.toString('hex') });
  }));
}
async function passwordMatches(password, user) {
  if (!user.salt || !user.passwordHash) return false;
  const result = await hashPassword(password, user.salt);
  const a = Buffer.from(result.hash, 'hex'); const b = Buffer.from(user.passwordHash, 'hex');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
async function saveUsers() {
  writeQueue = writeQueue.then(async () => {
    await fs.promises.mkdir(DATA_DIR, { recursive: true });
    const temp = `${USERS_FILE}.${process.pid}.tmp`;
    await fs.promises.writeFile(temp, JSON.stringify(users, null, 2), { mode: 0o600 });
    await fs.promises.rename(temp, USERS_FILE);
  });
  return writeQueue;
}
async function initializeUsers() {
  await fs.promises.mkdir(DATA_DIR, { recursive: true });
  try { users = JSON.parse(await fs.promises.readFile(USERS_FILE, 'utf8')); if (!Array.isArray(users)) users = []; }
  catch (_) { users = []; }
  if (ADMIN_EMAIL && ADMIN_PASSWORD && !users.some(u => u.email === ADMIN_EMAIL)) {
    if (ADMIN_PASSWORD.length < 12) throw new Error('BOOTSTRAP_ADMIN_PASSWORD debe tener al menos 12 caracteres.');
    const pw = await hashPassword(ADMIN_PASSWORD);
    users.push({ id: crypto.randomUUID(), name: 'Administrador Afrogo', email: ADMIN_EMAIL, role: 'admin', status: 'approved', createdAt: new Date().toISOString(), salt: pw.salt, passwordHash: pw.hash });
    await saveUsers();
    console.log('Cuenta inicial de administrador creada desde variables de entorno.');
  }
}
async function readJson(req) {
  const chunks = []; let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY) throw Object.assign(new Error('El cuerpo de la solicitud es demasiado grande.'), { status: 413 });
    chunks.push(chunk);
  }
  if (!size) return {};
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch (_) { throw Object.assign(new Error('JSON inválido.'), { status: 400 }); }
}
function rateLimited(req, key) {
  const ip = String(req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown').split(',')[0].trim();
  const k = `${key}:${ip}`; const now = Date.now(); const prev = loginAttempts.get(k) || { count: 0, start: now };
  if (now - prev.start > 15 * 60 * 1000) { prev.count = 0; prev.start = now; }
  prev.count++; loginAttempts.set(k, prev);
  return prev.count > 12;
}
function azuraUrl(endpoint) { return `${AZURA_BASE}${endpoint.startsWith('/') ? '' : '/'}${endpoint}`; }
async function azuraFetch(endpoint, options = {}) {
  if (!AZURA_BASE || !AZURA_KEY || !STATION_ID) throw Object.assign(new Error('Falta configurar AZURACAST_BASE_URL, AZURACAST_STATION_ID o AZURACAST_API_KEY en el servidor.'), { status: 503 });
  const headers = new Headers(options.headers || {});
  headers.set('X-API-Key', AZURA_KEY);
  headers.set('Accept', options.accept || 'application/json, audio/*, image/*, */*');
  const response = await fetch(azuraUrl(endpoint), { ...options, headers, signal: AbortSignal.timeout(30000), redirect: 'follow' });
  return response;
}
function normalizePath(p) { return String(p || '').replace(/\\/g, '/').replace(/^\/+/, ''); }
function inferFromFilename(pathValue) {
  const ext = path.extname(pathValue || '');
  let base = path.basename(pathValue || '', ext).replace(/_/g, ' ').replace(/\s+/g, ' ').trim();
  base = base.replace(/\s*[\[(](official video|official audio|lyrics?|visualizer|audio|video)[^\])]*[\])]?\s*/ig, ' ').trim();
  // Remove a leading track number such as "01 - ", "03." or "12_".
  base = base.replace(/^\s*\d{1,3}(?:[\s._-]+)+/, '').trim();
  const parts = base.split(/\s+-\s+/).map(v => v.trim()).filter(Boolean);
  if (parts.length >= 2) return { title: parts.slice(1).join(' - '), artist: parts[0] };
  return { title: base, artist: '' };
}
function extractArtId(media, row) {
  const candidates = [media.art, media.links && media.links.art, row.art, row.links && row.links.art, media.unique_id, media.uniqueId];
  for (const c of candidates) {
    if (typeof c !== 'string' || !c) continue;
    const match = c.match(/\/art\/([^/?#]+)/);
    if (match) return match[1];
    if (/^[a-f0-9]{12,}$/i.test(c)) return c;
  }
  return null;
}
function fileIdFrom(row, media) {
  const candidates = [media.media_id, media.file_id, row.media_id, row.file_id, media.id, row.id];
  for (const val of candidates) if (/^\d+$/.test(String(val ?? ''))) return String(val);
  const playLink = (media.links && media.links.play) || (row.links && row.links.play) || '';
  const match = String(playLink).match(/\/file\/(\d+)\/play(?:$|\?)/);
  return match ? match[1] : null;
}
function normalizeMedia(row) {
  const media = row && (row.media || row) || {};
  const id = fileIdFrom(row || {}, media);
  if (!id) return null;
  const pathValue = normalizePath(media.path || row.path || row.path_short || media.filename || '');
  const filenameMeta = inferFromFilename(pathValue);
  const textValue = String(media.text || row.text || '').trim();
  const textParts = textValue.split(/\s+-\s+/).map(v => v.trim()).filter(Boolean);
  const textTitle = textParts.length >= 2 ? textParts.slice(1).join(' - ') : '';
  const textArtist = textParts.length >= 2 ? textParts[0] : '';
  const title = String(media.title || (media.song && media.song.title) || row.title || row.name || textTitle || filenameMeta.title || 'Canción sin título').trim();
  const artist = String(media.artist || (media.song && media.song.artist) || row.artist || textArtist || filenameMeta.artist || '').trim() || 'Artista desconocido';
  const album = String(media.album || (media.song && media.song.album) || row.album || '').trim();
  const genre = String(media.genre || row.genre || '').split(/[;,]/)[0].trim() || 'Sin categoría';
  const durationRaw = Number(media.length ?? media.duration ?? row.length ?? row.duration ?? 0);
  const trackNumber = Number((media.track && (media.track.no || media.track)) || media.track_number || row.trackNumber || 0) || 0;
  const discNumber = Number((media.disk && (media.disk.no || media.disk)) || media.disc_number || row.discNumber || 0) || 0;
  return { id, title, artist, album, genre, duration: Number.isFinite(durationRaw) ? durationRaw : 0, trackNumber, discNumber, path: pathValue, artId: extractArtId(media, row || {}), isPlayable: media.is_playable !== false && row.is_playable !== false };
}
function extractRows(body) {
  if (Array.isArray(body)) return body;
  for (const key of ['files', 'items', 'data', 'rows', 'results']) if (body && Array.isArray(body[key])) return body[key];
  if (body && body.data && Array.isArray(body.data.items)) return body.data.items;
  return [];
}
function makeMediaTicket(track, scope) {
  return signToken({ kind: 'media', scope, trackId: String(track.id), artId: track.artId || null }, MEDIA_TICKET_SECONDS);
}
function publicTrack(track) {
  const id = encodeURIComponent(track.id);
  const origin = PUBLIC_BASE || `http://127.0.0.1:${PORT}`;
  const playTicket = makeMediaTicket(track, 'play');
  const artTicket = track.artId ? makeMediaTicket(track, 'art') : null;
  return {
    id: track.id, title: track.title, artist: track.artist, album: track.album || '', genre: track.genre,
    duration: track.duration, trackNumber: track.trackNumber, discNumber: track.discNumber,
    coverUrl: artTicket ? `${origin}/api/tracks/${id}/art?ticket=${encodeURIComponent(artTicket)}` : '',
    playUrl: `${origin}/api/tracks/${id}/play?ticket=${encodeURIComponent(playTicket)}`,
    source: 'azuracast', path: track.path,
  };
}
async function getCatalog() {
  let response = await azuraFetch(`/api/station/${encodeURIComponent(STATION_ID)}/files`);
  if (!response.ok) {
    const firstStatus = response.status;
    // Fallback to the Media Manager listing format used by several AzuraCast releases.
    response = await azuraFetch(`/api/station/${encodeURIComponent(STATION_ID)}/files/list?internal=true&rowCount=1000&current=1&currentDirectory=&sortOrder=ASC&page=1`);
    if (!response.ok) throw Object.assign(new Error(`AzuraCast no pudo listar archivos (HTTP ${firstStatus}/${response.status}). Revisa el ID de estación y los permisos de la nueva API Key.`), { status: 502 });
  }
  const body = await response.json();
  const items = extractRows(body).map(normalizeMedia).filter(Boolean).filter(t => t.isPlayable);
  // Si la API devuelve media anidada, el normalizador conserva los IDs de archivo utilizables.
  const deduped = [...new Map(items.map(t => [t.id, t])).values()];
  return deduped.map(publicTrack);
}
async function proxyMedia(req, res, kind, id, ticket) {
  const p = verifyToken(ticket);
  if (!p || p.kind !== 'media' || p.scope !== kind || String(p.trackId) !== String(id)) return json(res, 401, { error: 'Enlace multimedia vencido o inválido. Actualiza el catálogo e inténtalo de nuevo.' });
  let endpoint;
  if (kind === 'play') endpoint = `/api/station/${encodeURIComponent(STATION_ID)}/file/${encodeURIComponent(id)}/play`;
  else {
    if (!p.artId) return json(res, 404, { error: 'Esta canción no tiene portada en AzuraCast.' });
    endpoint = `/api/station/${encodeURIComponent(STATION_ID)}/art/${encodeURIComponent(p.artId)}`;
  }
  const headers = {};
  if (kind === 'play' && req.headers.range) headers.Range = req.headers.range;
  let upstream;
  try { upstream = await azuraFetch(endpoint, { headers, accept: kind === 'play' ? 'audio/*, application/octet-stream;q=0.9, */*;q=0.5' : 'image/*, */*;q=0.5' }); }
  catch (e) { return json(res, 502, { error: `No se pudo conectar con AzuraCast: ${e.message}` }); }
  if (!upstream.ok && upstream.status !== 206) {
    await upstream.body?.cancel().catch(() => {});
    return json(res, upstream.status === 404 ? 404 : 502, { error: `AzuraCast no pudo entregar el ${kind === 'play' ? 'audio' : 'arte'} (HTTP ${upstream.status}).` });
  }
  const headersOut = {
    'Access-Control-Allow-Origin': '*',
    'Cache-Control': 'private, max-age=60',
    'Content-Type': upstream.headers.get('content-type') || (kind === 'play' ? 'audio/mpeg' : 'image/jpeg'),
  };
  for (const h of ['content-length', 'content-range', 'accept-ranges', 'etag', 'last-modified']) {
    const v = upstream.headers.get(h); if (v) headersOut[h] = v;
  }
  res.writeHead(upstream.status, headersOut);
  if (!upstream.body) return res.end();
  Readable.fromWeb(upstream.body).pipe(res);
  req.on('close', () => { if (!res.writableEnded) upstream.body.cancel().catch(() => {}); });
}

async function handle(req, res) {
  const parsed = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  const pathname = decodeURIComponent(parsed.pathname);
  if (req.method === 'OPTIONS') { res.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Authorization, Content-Type, Range', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS' }); return res.end(); }
  if (req.method === 'GET' && pathname === '/health') return json(res, 200, {
    ok: true, service: 'afrogo-azuracast-gateway', version: '1.0.1',
    azuracastConfigured: Boolean(AZURA_BASE && STATION_ID && AZURA_KEY),
    stationConfigured: Boolean(STATION_ID), httpsRequiredInProduction: true,
  });

  if (req.method === 'POST' && pathname === '/api/auth/register') {
    if (!requireHttps(req)) return json(res, 426, { error: 'Configura HTTPS antes de enviar contraseñas. Acceso seguro requerido.' });
    if (rateLimited(req, 'register')) return json(res, 429, { error: 'Demasiados intentos. Inténtalo más tarde.' });
    const input = await readJson(req);
    const email = String(input.email || '').trim().toLowerCase();
    const password = String(input.password || '');
    const name = String(input.name || email.split('@')[0] || '').trim().slice(0, 80);
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return json(res, 400, { error: 'Introduce un correo válido.' });
    if (password.length < 10) return json(res, 400, { error: 'La contraseña debe tener al menos 10 caracteres.' });
    if (users.some(u => u.email === email)) return json(res, 409, { error: 'Ya existe una cuenta o solicitud para este correo.' });
    const pw = await hashPassword(password);
    const user = { id: crypto.randomUUID(), name, email, role: 'user', status: 'pending', createdAt: new Date().toISOString(), salt: pw.salt, passwordHash: pw.hash };
    users.push(user); await saveUsers();
    return json(res, 201, { message: 'Solicitud enviada. El administrador debe aprobarla antes de iniciar sesión.', user: safeUser(user) });
  }
  if (req.method === 'POST' && pathname === '/api/auth/login') {
    if (!requireHttps(req)) return json(res, 426, { error: 'Configura HTTPS antes de enviar contraseñas. Acceso seguro requerido.' });
    if (rateLimited(req, 'login')) return json(res, 429, { error: 'Demasiados intentos. Inténtalo más tarde.' });
    const input = await readJson(req);
    const email = String(input.email || '').trim().toLowerCase();
    const password = String(input.password || '');
    const user = users.find(u => u.email === email);
    if (!user || !(await passwordMatches(password, user))) return json(res, 401, { error: 'Correo o contraseña incorrectos.' });
    if (user.status === 'pending') return json(res, 403, { error: 'Tu solicitud está pendiente de aprobación.' });
    if (user.status !== 'approved') return json(res, 403, { error: 'Esta cuenta no tiene acceso activo.' });
    const token = signToken({ kind: 'user', sub: user.id, role: user.role }, TOKEN_LIFETIME_SECONDS);
    return json(res, 200, { token, expiresIn: TOKEN_LIFETIME_SECONDS, user: safeUser(user) });
  }
  if (req.method === 'GET' && pathname === '/api/auth/me') {
    if (!requireHttps(req)) return json(res, 426, { error: 'Configura HTTPS antes de enviar tokens de sesión.' });
    const user = authUser(req); return user ? json(res, 200, { user: safeUser(user) }) : json(res, 401, { error: 'Sesión vencida o no válida.' });
  }
  if (req.method === 'GET' && pathname === '/api/admin/users') {
    if (!requireHttps(req)) return json(res, 426, { error: 'Configura HTTPS antes de enviar tokens de sesión.' });
    if (!adminUser(req)) return json(res, 403, { error: 'Solo un administrador aprobado puede gestionar usuarios.' });
    return json(res, 200, { users: users.map(safeUser).sort((a,b) => a.status.localeCompare(b.status) || a.email.localeCompare(b.email)) });
  }
  const adminMatch = pathname.match(/^\/api\/admin\/users\/([a-f0-9-]+)\/(approve|reject|revoke)$/i);
  if (req.method === 'POST' && adminMatch) {
    if (!requireHttps(req)) return json(res, 426, { error: 'Configura HTTPS antes de enviar tokens de sesión.' });
    if (!adminUser(req)) return json(res, 403, { error: 'Solo un administrador aprobado puede gestionar usuarios.' });
    const [, id, action] = adminMatch; const target = users.find(u => u.id === id);
    if (!target) return json(res, 404, { error: 'Usuario no encontrado.' });
    if (target.role === 'admin') return json(res, 400, { error: 'No se pueden cambiar las cuentas de administrador desde esta lista.' });
    if (action === 'reject') users = users.filter(u => u.id !== id);
    else if (action === 'approve') { target.status = 'approved'; target.approvedAt = new Date().toISOString(); }
    else if (action === 'revoke') target.status = 'revoked';
    await saveUsers(); return json(res, 200, { ok: true, users: users.map(safeUser) });
  }
  if (req.method === 'POST' && pathname === '/api/admin/tracks/upload') {
    if (!AZURA_UPLOAD_ENABLED) return json(res, 503, { error: 'Las subidas están desactivadas por seguridad. Actualiza AzuraCast a una versión parcheada, confirma el endpoint de carga de tu instalación y luego configura AZURACAST_UPLOAD_ENABLED=true en el servidor.' });
    if (!requireHttps(req)) return json(res, 426, { error: 'Configura HTTPS antes de subir archivos.' });
    if (!adminUser(req)) return json(res, 403, { error: 'Solo un administrador aprobado puede subir música al catálogo central.' });
    const contentType = String(req.headers['content-type'] || '');
    if (!contentType.toLowerCase().startsWith('multipart/form-data;')) return json(res, 415, { error: 'La carga debe usar multipart/form-data.' });
    if (!AZURA_KEY || !STATION_ID || !AZURA_BASE) return json(res, 503, { error: 'AzuraCast no está configurado en el servidor.' });
    let upstream;
    try {
      // El audio se transmite en streaming al endpoint Media Manager de AzuraCast; nunca se guarda en este backend.
      upstream = await fetch(azuraUrl(`/api/station/${encodeURIComponent(STATION_ID)}/files/upload`), {
        method: 'POST',
        headers: { 'X-API-Key': AZURA_KEY, 'Content-Type': contentType, Accept: 'application/json, */*' },
        body: Readable.toWeb(req), duplex: 'half', signal: AbortSignal.timeout(10 * 60 * 1000), redirect: 'follow'
      });
    } catch (e) { return json(res, 502, { error: `No se pudo subir el archivo a AzuraCast: ${e.message}` }); }
    const raw = await upstream.text().catch(() => '');
    if (!upstream.ok) {
      let detail = raw;
      try { const j = JSON.parse(raw); detail = j.message || j.error || raw; } catch (_) {}
      return json(res, 502, { error: `AzuraCast rechazó la carga (HTTP ${upstream.status}). ${String(detail).slice(0, 500)} Revisa el endpoint de subida y los permisos de la API Key.` });
    }
    let result = {};
    try { result = raw ? JSON.parse(raw) : {}; } catch (_) { result = { message: raw.slice(0, 300) }; }
    return json(res, 201, { ok: true, message: 'Archivo enviado a AzuraCast.', result });
  }

  if (req.method === 'GET' && pathname === '/api/catalog') {
    if (!requireHttps(req)) return json(res, 426, { error: 'Configura HTTPS antes de usar el catálogo de usuarios.' });
    if (!authUser(req)) return json(res, 401, { error: 'Inicia sesión con una cuenta aprobada.' });
    try {
      const items = await getCatalog();
      return json(res, 200, { source: 'AzuraCast', station: 'Afrogo FM', count: items.length, items });
    } catch (e) { return json(res, e.status || 502, { error: e.message || 'No se pudo consultar el catálogo de AzuraCast.' }); }
  }
  const mediaMatch = pathname.match(/^\/api\/tracks\/(\d+)\/(play|art)$/);
  if (req.method === 'GET' && mediaMatch) {
    if (!requireHttps(req)) return json(res, 426, { error: 'Configura HTTPS para proteger los enlaces temporales de audio e imagen.' });
    const [, id, kind] = mediaMatch;
    return proxyMedia(req, res, kind, id, String(parsed.searchParams.get('ticket') || ''));
  }
  return json(res, 404, { error: 'Ruta no encontrada.' });
}

const server = http.createServer((req, res) => {
  Promise.resolve(handle(req, res)).catch(err => {
    console.error('Petición fallida:', err && err.message ? err.message : 'error');
    if (!res.headersSent) json(res, err.status || 500, { error: err.status && err.status < 500 ? err.message : 'Error interno del servidor.' });
    else res.destroy();
  });
});

initializeUsers().then(() => {
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`Afrogo API escucha en el puerto ${PORT}.`);
    console.log(`Catálogo AzuraCast configurado: ${Boolean(AZURA_BASE && STATION_ID && AZURA_KEY)}.`);
    if (NODE_ENV === 'production' && !(PUBLIC_BASE.startsWith('https://'))) console.warn('ADVERTENCIA: PUBLIC_API_BASE_URL debe empezar por https:// antes de compartir la app.');
  });
}).catch(err => { console.error(`No se pudo iniciar Afrogo API: ${err.message}`); process.exit(1); });

process.on('SIGTERM', () => server.close(() => process.exit(0)));
process.on('SIGINT', () => server.close(() => process.exit(0)));
