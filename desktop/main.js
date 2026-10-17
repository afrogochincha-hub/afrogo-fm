const { app, BrowserWindow, ipcMain, protocol, net } = require('electron');
const path = require('path');
const fs = require('fs');
const fsp = fs.promises;
const { pathToFileURL } = require('url');
const crypto = require('crypto');
const { Readable } = require('stream');
const mm = require('music-metadata');

// Stable Windows identity so the branded icon is used by the window and taskbar.
if (process.platform === 'win32') {
  app.setAppUserModelId('com.afrogo.fm');
}

// Read runtime configuration in the main process (full Node.js is available here).
// Electron's sandboxed preload has a restricted require() and must not read app-config.json itself.
function loadAfrogoRuntimeConfig() {
  let apiBaseUrl = String(process.env.AFROGO_API_BASE_URL || '').trim();
  if (!apiBaseUrl) {
    try {
      const configPath = path.join(__dirname, 'app-config.json');
      const configText = fs.readFileSync(configPath, 'utf8').replace(/^\uFEFF/, '');
      const config = JSON.parse(configText);
      apiBaseUrl = String(config.apiBaseUrl || '').trim();
    } catch (error) {
      console.warn('[Afrogo FM] No se pudo leer app-config.json:', error.message);
    }
  }
  apiBaseUrl = apiBaseUrl.replace(/\/+$/, '');
  const allowInsecureHttp = process.env.AFROGO_ALLOW_INSECURE_HTTP === '1';
  return { apiBaseUrl, allowInsecureHttp };
}

protocol.registerSchemesAsPrivileged([{ scheme: 'afrogo-media', privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true } }]);
const allowedExt = new Set(['.mp3','.m4a','.ogg','.wav','.flac','.aac','.opus']);
const libraryDir = () => path.join(app.getPath('userData'), 'library');
const dbPath = () => path.join(libraryDir(), 'library.json');
async function readLibrary() {
  try { return JSON.parse(await fsp.readFile(dbPath(), 'utf8')); }
  catch (e) { if (e.code === 'ENOENT') return []; throw e; }
}
async function writeLibrary(tracks) {
  await fsp.mkdir(libraryDir(), { recursive: true });
  const tmp = dbPath() + '.tmp';
  await fsp.writeFile(tmp, JSON.stringify(tracks, null, 2), 'utf8');
  await fsp.rename(tmp, dbPath());
}

function genreNamesDoesNotExist(value) { return !['Pop','Cumbia','Salsa','Urbano','Afrobeat','Baladas','Electrónica','Rock','Reguetón','Merengue','Regional','Jazz'].includes(value); }

function classifyGenre(track, common = {}) {
  const rawTags = [...(Array.isArray(common.genre) ? common.genre : []), track.genre || ''];
  const tags = rawTags.map(x => String(x || '').toLowerCase()).join(' ');
  const text = [track.title, track.artist, track.album, track.fileName, ...rawTags].filter(Boolean).join(' ').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  const rules = [
    ['Reguetón', /reggaeton|regueton|perreo|dembow/],
    ['Cumbia', /cumbia|chicha|sonidera|santafesina/],
    ['Salsa', /salsa|son cubano|timba/],
    ['Merengue', /merengue|mambo dominicano/],
    ['Afrobeat', /afrobeats|afrobeat|afropop|amapiano|afro house/],
    ['Electrónica', /electronica|electronic|techno|house music|trance|edm|dance music|deep house|tech house/],
    ['Rock', /(^|\W)rock(\W|$)|metal|punk|grunge|indie rock/],
    ['Jazz', /jazz|bebop|swing/],
    ['Baladas', /balada|romantica|romantic|bolero|baladas/],
    ['Regional', /regional mexicano|banda sinaloense|nortena|norteña|corridos|ranchera|huapango/],
    ['Urbano', /urbano|trap latino|latin trap|hip hop|hip-hop|rap latino|rap/],
    ['Pop', /(^|\W)pop(\W|$)|synthpop|k-pop|j-pop/]
  ];
  for (const [genre, re] of rules) if (re.test(tags) || re.test(text)) return genre;
  if (/latin|latino|tropical/.test(tags)) return 'Salsa';
  return track.genre && track.genre !== 'Sin categoría' ? track.genre : 'Pop';
}

function publicTrack(track) {
  const { filePath, ...safe } = track;
  return { ...safe, url: `afrogo-media://local/${encodeURIComponent(track.id)}` };
}


function normalizeMusicText(value) {
  return String(value || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/\.(mp3|m4a|ogg|wav|flac|aac|opus)$/i, '')
    .replace(/\b(official video|official audio|video oficial|audio oficial|lyrics?|letra|visualizer|sub español|hd|4k|remastered|remasterizado)\b/gi, ' ')
    .replace(/\[[^\]]*\]|\([^)]*\)/g, ' ').replace(/[^a-z0-9]+/g, ' ').trim();
}
function tokenSimilarity(a, b) {
  const aa = new Set(normalizeMusicText(a).split(/\s+/).filter(x => x.length > 1));
  const bb = new Set(normalizeMusicText(b).split(/\s+/).filter(x => x.length > 1));
  if (!aa.size || !bb.size) return 0;
  let common = 0; for (const x of aa) if (bb.has(x)) common++;
  return common / Math.max(aa.size, bb.size);
}
function findDuplicateTrack(candidate, tracks) {
  const title = normalizeMusicText(candidate.title || candidate.fileName || '');
  if (!title) return null;
  const artist = normalizeMusicText(candidate.artist || '');
  let best = null;
  for (const existing of tracks) {
    const otherTitle = normalizeMusicText(existing.title || existing.fileName || '');
    if (!otherTitle) continue;
    const exactTitle = title === otherTitle;
    const similarity = tokenSimilarity(title, otherTitle);
    const otherArtist = normalizeMusicText(existing.artist || '');
    const artistsKnown = artist && otherArtist && artist !== 'artista desconocido' && otherArtist !== 'artista desconocido';
    const sameArtist = artistsKnown && (artist === otherArtist || tokenSimilarity(artist, otherArtist) >= 0.8);
    const artistConflict = artistsKnown && !sameArtist;
    // Same title is always a duplicate. Similar titles are duplicates only when
    // the artist agrees or at least one artist is unknown, to avoid false positives.
    if (exactTitle || (similarity >= 0.88 && !artistConflict)) {
      const score = exactTitle ? 1 : similarity;
      if (!best || score > best.score) best = { track: existing, score, exactTitle };
    }
  }
  return best;
}

async function lookupMusicMetadata(track) {
  // Search by the actual title (or filename when the audio has no embedded title).
  const titleQuery = String(track.title || path.basename(track.fileName || '', path.extname(track.fileName || ''))).trim();
  if (!titleQuery || titleQuery.length < 2) return { found: false, reason: 'No hay un título suficiente para buscar.' };
  const query = [titleQuery, track.artist && track.artist !== 'Artista desconocido' ? track.artist : ''].filter(Boolean).join(' ');
  try {
    const endpoint = 'https://itunes.apple.com/search?media=music&entity=song&limit=15&term=' + encodeURIComponent(query);
    const response = await fetch(endpoint, { headers: { 'User-Agent': 'AfrogoFM/3.5 metadata lookup' }, signal: AbortSignal.timeout(9000) });
    if (!response.ok) throw new Error('Servicio de búsqueda respondió ' + response.status);
    const data = await response.json();
    const results = Array.isArray(data.results) ? data.results : [];
    if (!results.length) return { found: false, reason: 'No se encontró una coincidencia en el catálogo musical.' };
    const ranked = results.map(item => {
      const titleScore = tokenSimilarity(titleQuery, item.trackName || '');
      const artistKnown = track.artist && track.artist !== 'Artista desconocido';
      const artistScore = artistKnown ? tokenSimilarity(track.artist, item.artistName || '') : 0;
      const exact = normalizeMusicText(titleQuery) === normalizeMusicText(item.trackName || '');
      return { item, score: titleScore * (artistKnown ? 0.82 : 1) + (artistKnown ? artistScore * 0.18 : 0), exact };
    }).sort((a,b) => b.score - a.score);
    const best = ranked[0];
    if (!best || (!best.exact && best.score < 0.48)) return { found: false, reason: 'No hubo coincidencia suficientemente fiable.' };
    const item = best.item;
    let cover = item.artworkUrl100 || item.artworkUrl60 || '';
    if (cover) cover = cover.replace(/100x100bb\.(jpg|png)/i, '600x600bb.$1');
    return { found: true, title: item.trackName || track.title, artist: item.artistName || '', album: item.collectionName || '', cover, score: Math.round(best.score * 100) };
  } catch (error) {
    console.warn('[Afrogo FM] No se pudieron consultar metadatos online:', error.message);
    return { found: false, reason: 'No se pudo conectar con el servicio de metadatos.' };
  }
}

async function saveTrack(_event, input) {
  if (!input || typeof input.name !== 'string' || !input.bytes) throw new Error('Archivo de audio inválido.');
  const ext = path.extname(input.name).toLowerCase();
  if (!allowedExt.has(ext)) throw new Error('Formato de audio no admitido.');
  const id = crypto.randomUUID();
  await fsp.mkdir(libraryDir(), { recursive: true });
  const filePath = path.join(libraryDir(), `${id}${ext}`);
  const bytes = Buffer.from(input.bytes);
  if (!bytes.length) throw new Error('El archivo está vacío.');
  await fsp.writeFile(filePath, bytes);
  let meta = {};
  try { meta = await mm.parseFile(filePath, { duration: true, skipCovers: false }); } catch (e) { /* Some formats do not expose tags through parseFile. */ }
  let common = meta.common || {};
  // Retry against the original bytes when a container's parser did not expose embedded artwork.
  if (!(common.picture && common.picture.length)) {
    try { const buffered = await mm.parseBuffer(bytes, input.mimeType || undefined, { duration: true, skipCovers: false }); if (buffered.common) { common = { ...buffered.common, ...common, picture: (common.picture && common.picture.length) ? common.picture : buffered.common.picture }; if (!meta.format || !meta.format.duration) meta.format = buffered.format; } } catch (e) { /* Keep the upload even if metadata is absent. */ }
  }
  const picture = common.picture && common.picture[0];
  const pictureMime = picture && (picture.format || picture.mime || 'image/jpeg');
  const cover = picture && picture.data ? `data:${pictureMime};base64,${Buffer.from(picture.data).toString('base64')}` : '';
  const track = {
    id,
    title: common.title || path.basename(input.name, ext),
    artist: common.artist || (common.artists && common.artists.join(', ')) || 'Artista desconocido',
    album: common.album || 'Biblioteca Afrogo',
    genre: classifyGenre({ title: common.title || path.basename(input.name, ext), artist: common.artist || (common.artists && common.artists.join(', ')) || '', album: common.album || '', fileName: path.basename(input.name), genre: Array.isArray(common.genre) && common.genre.length ? common.genre[0] : '' }, common),
    emoji: '♫',
    cover,
    duration: Math.round(meta.format && meta.format.duration || 0),
    trackNumber: Number(common.track && common.track.no) || 0,
    discNumber: Number(common.disk && common.disk.no) || 0,
    fileName: path.basename(input.name),
    filePath,
    addedAt: new Date().toISOString()
  };
  // If title/artist/cover tags are missing, look up the track by its title online.
  const missingArtist = !common.artist && !(common.artists && common.artists.length) || track.artist === 'Artista desconocido';
  const missingCover = !track.cover;
  const missingAlbum = !common.album || !String(common.album).trim();
  if (missingArtist || missingCover || missingAlbum || !common.title) {
    const lookup = await lookupMusicMetadata(track);
    if (lookup.found) {
      if (!common.title && lookup.title) track.title = lookup.title;
      if (missingArtist && lookup.artist) track.artist = lookup.artist;
      if ((missingAlbum || !track.album || track.album === 'Biblioteca Afrogo') && lookup.album) track.album = lookup.album;
      if (missingCover && lookup.cover) track.cover = lookup.cover;
      track.metadataSource = 'iTunes Search';
      track.metadataMatch = lookup.score;
      track.genre = classifyGenre(track, common);
    } else {
      track.metadataLookup = lookup.reason;
    }
  }
  const tracks = await readLibrary();
  const duplicate = findDuplicateTrack(track, tracks);
  if (duplicate) {
    try { await fsp.unlink(filePath); } catch (e) { /* Ignore cleanup failures. */ }
    return { duplicate: true, duplicateOf: publicTrack(duplicate.track), similarity: Math.round(duplicate.score * 100), track: publicTrack(track) };
  }
  tracks.unshift(track);
  await writeLibrary(tracks);
  return publicTrack(track);
}

const mimeByExt = {'.mp3':'audio/mpeg','.m4a':'audio/mp4','.aac':'audio/aac','.ogg':'audio/ogg','.opus':'audio/ogg','.wav':'audio/wav','.flac':'audio/flac'};
async function scanExistingAlbums() {
  const tracks = await readLibrary();
  const normalizedAlbum = value => String(value || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, ' ').trim();
  const isMissingAlbum = track => !normalizedAlbum(track.album) || ['biblioteca afrogo', 'subido por administracion', 'sin album', 'unknown album', 'unknown', 'album desconocido'].includes(normalizedAlbum(track.album));
  const candidates = tracks.filter(track => track.filePath && isMissingAlbum(track));
  let updated = 0;
  let checked = 0;
  let notFound = 0;
  for (let i = 0; i < candidates.length; i += 4) {
    await Promise.all(candidates.slice(i, i + 4).map(async track => {
      checked++;
      try {
        const meta = await mm.parseFile(track.filePath, { duration: true, skipCovers: false });
        const common = meta.common || {};
        let changed = false;
        if (common.album && String(common.album).trim()) { track.album = String(common.album).trim(); changed = true; }
        if ((!track.artist || track.artist === 'Artista desconocido') && (common.artist || (common.artists && common.artists.length))) { track.artist = common.artist || common.artists.join(', '); changed = true; }
        const picture = common.picture && common.picture[0];
        if (!track.cover && picture && picture.data) { track.cover = `data:${picture.format || picture.mime || 'image/jpeg'};base64,${Buffer.from(picture.data).toString('base64')}`; changed = true; }
        if ((!track.trackNumber || !Number(track.trackNumber)) && common.track && common.track.no) { track.trackNumber = Number(common.track.no) || 0; changed = true; }
        if ((!track.discNumber || !Number(track.discNumber)) && common.disk && common.disk.no) { track.discNumber = Number(common.disk.no) || 0; changed = true; }
        if (isMissingAlbum(track)) {
          const lookup = await lookupMusicMetadata(track);
          if (lookup.found && lookup.album) { track.album = lookup.album; changed = true; }
          if (lookup.found && (!track.artist || track.artist === 'Artista desconocido') && lookup.artist) { track.artist = lookup.artist; changed = true; }
          if (lookup.found && !track.cover && lookup.cover) { track.cover = lookup.cover; changed = true; }
          if (lookup.found && lookup.title && track.title === path.basename(track.fileName || track.filePath, path.extname(track.filePath))) { track.title = lookup.title; changed = true; }
        }
        if (changed) updated++;
        if (isMissingAlbum(track)) notFound++;
      } catch (error) {
        notFound++;
        console.warn('[Afrogo FM] No se pudo analizar el álbum de', track.fileName, error.message);
      }
    }));
  }
  if (updated) await writeLibrary(tracks);
  return { tracks: tracks.map(publicTrack), checked, updated, notFound };
}

async function deleteTrack(_event, id) {
  if (typeof id !== 'string' || !id.trim()) throw new Error('Identificador de canción inválido.');
  const tracks = await readLibrary();
  const index = tracks.findIndex(t => t.id === id);
  if (index < 0) throw new Error('No se encontró la canción en la biblioteca.');
  const [track] = tracks.splice(index, 1);
  // Persist the catalog change first; only remove files stored inside Afrogo's library folder.
  await writeLibrary(tracks);
  if (track.filePath) {
    const root = path.resolve(libraryDir()) + path.sep;
    const target = path.resolve(track.filePath);
    if (target.startsWith(root)) {
      try { await fsp.unlink(target); }
      catch (error) { if (error.code !== 'ENOENT') console.warn('[Afrogo FM] No se pudo borrar el archivo de audio:', error.message); }
    }
  }
  return { success: true, id };
}

async function updateTrack(_event, input) {
  if (!input || typeof input.id !== 'string' || !input.updates) throw new Error('Datos de edición inválidos.');
  const tracks = await readLibrary();
  const track = tracks.find(t => t.id === input.id);
  if (!track) throw new Error('No se encontró la canción.');
  for (const key of ['title','artist','album','genre','lyrics']) {
    if (typeof input.updates[key] === 'string') track[key] = input.updates[key].trim().slice(0, 300);
  }
  if (typeof input.updates.cover === 'string') {
    if (input.updates.cover.length > 12 * 1024 * 1024) throw new Error('La portada es demasiado grande (máximo aproximado 9 MB).');
    if (!input.updates.cover || /^data:image\/(png|jpeg|jpg|webp|gif|bmp);base64,/i.test(input.updates.cover)) track.cover = input.updates.cover;
    else throw new Error('Formato de portada no admitido.');
  }
  await writeLibrary(tracks);
  return publicTrack(track);
}

app.whenReady().then(async () => {
  await fsp.mkdir(libraryDir(), { recursive: true });
  protocol.handle('afrogo-media', async (request) => {
    try {
      const id = decodeURIComponent(new URL(request.url).pathname.replace(/^\//, ''));
      const tracks = await readLibrary();
      const track = tracks.find(t => t.id === id);
      if (!track || !track.filePath) return new Response('Not found', { status: 404 });
      const stat = await fsp.stat(track.filePath);
      const size = stat.size;
      const type = mimeByExt[path.extname(track.filePath).toLowerCase()] || 'application/octet-stream';
      const headers = { 'Content-Type': type, 'Accept-Ranges': 'bytes', 'Cache-Control': 'no-cache' };
      if (request.method === 'HEAD') return new Response(null, { status: 200, headers: { ...headers, 'Content-Length': String(size) } });
      // Serve the complete original file and support standard HTTP byte ranges.
      // This is required by Chromium's media player for seeking and long audio files.
      const range = request.headers.get('range');
      if (range) {
        const match = /^bytes=(\d*)-(\d*)$/.exec(range.trim());
        if (!match || (!match[1] && !match[2])) {
          return new Response(null, { status: 416, headers: { ...headers, 'Content-Range': `bytes */${size}` } });
        }
        let start;
        let end;
        if (!match[1]) {
          const suffixLength = Number(match[2]);
          if (!Number.isSafeInteger(suffixLength) || suffixLength <= 0) return new Response(null, { status: 416, headers: { ...headers, 'Content-Range': `bytes */${size}` } });
          start = Math.max(0, size - suffixLength);
          end = size - 1;
        } else {
          start = Number(match[1]);
          end = match[2] ? Number(match[2]) : size - 1;
        }
        if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 0 || start >= size || end < start) {
          return new Response(null, { status: 416, headers: { ...headers, 'Content-Range': `bytes */${size}` } });
        }
        end = Math.min(end, size - 1);
        const stream = fs.createReadStream(track.filePath, { start, end });
        return new Response(Readable.toWeb(stream), { status: 206, headers: { ...headers, 'Content-Length': String(end - start + 1), 'Content-Range': `bytes ${start}-${end}/${size}` } });
      }
      const stream = fs.createReadStream(track.filePath);
      return new Response(Readable.toWeb(stream), { status: 200, headers: { ...headers, 'Content-Length': String(size) } });
    } catch (e) { console.error('Media protocol error', e); return new Response('Media error', { status: 500 }); }
  });
  ipcMain.handle('library:list', async () => {
    const tracks = await readLibrary();
    let changed = false;
    // Reintenta extraer portadas para canciones antiguas que se guardaron sin imagen.
    for (const track of tracks) {
      if (!track.filePath) continue;
      try {
        const meta = await mm.parseFile(track.filePath, { duration: true, skipCovers: false });
        const common = meta.common || {};
        const pic = common.picture && common.picture[0];
        if (!track.cover && pic && pic.data) { track.cover = `data:${pic.format || pic.mime || 'image/jpeg'};base64,${Buffer.from(pic.data).toString('base64')}`; changed = true; }
        if ((!track.title || track.title === path.basename(track.fileName || track.filePath, path.extname(track.filePath))) && common.title) { track.title = common.title; changed = true; }
        if ((!track.artist || track.artist === 'Artista desconocido') && (common.artist || (common.artists && common.artists.length))) { track.artist = common.artist || common.artists.join(', '); changed = true; }
        if ((!track.album || track.album === 'Biblioteca Afrogo') && common.album) { track.album = common.album; changed = true; }
        if ((!track.trackNumber || track.trackNumber === 0) && common.track && common.track.no) { track.trackNumber = Number(common.track.no) || 0; changed = true; }
        if ((!track.discNumber || track.discNumber === 0) && common.disk && common.disk.no) { track.discNumber = Number(common.disk.no) || 0; changed = true; }
        const detectedGenre = classifyGenre(track, common);
        if ((!track.genre || track.genre === 'Sin categoría' || genreNamesDoesNotExist(track.genre)) && detectedGenre) { track.genre = detectedGenre; changed = true; }
        if ((!track.duration || track.duration === 0) && meta.format && meta.format.duration) { track.duration = Math.round(meta.format.duration); changed = true; }
      } catch (_) {}
    }
    if (changed) await writeLibrary(tracks);
    return tracks.map(publicTrack);
  });
  ipcMain.handle('library:save', saveTrack);
  ipcMain.handle('library:update', updateTrack);
  ipcMain.handle('library:delete', deleteTrack);
  ipcMain.handle('library:scanAlbums', scanExistingAlbums);
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});

function createWindow() {
  const runtimeConfig = loadAfrogoRuntimeConfig();
  const win = new BrowserWindow({
    width: 1280, height: 850, minWidth: 900, minHeight: 650,
    title: 'Afrogo FM', icon: path.join(__dirname, process.platform === 'win32' ? 'afrogo-icon.ico' : 'afrogo-icon.png'), backgroundColor: '#100D14', autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      // Pass only non-secret public configuration to the sandboxed preload.
      additionalArguments: [
        `--afrogo-api-base-url=${runtimeConfig.apiBaseUrl}`,
        `--afrogo-allow-insecure-http=${runtimeConfig.allowInsecureHttp ? '1' : '0'}`
      ]
    }
  });
  // Evita abrir pestañas o ventanas externas; la radio se reproduce dentro de la app.
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.loadFile(path.join(__dirname, 'app', 'index.html'));
}
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
