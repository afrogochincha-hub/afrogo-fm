const { test } = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');

async function unusedPort() {
  const server = net.createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}

async function waitFor(url, child) {
  for (let i = 0; i < 80; i++) {
    if (child.exitCode !== null) throw new Error('Gateway test process exited early.');
    try { const response = await fetch(url); if (response.ok) return; } catch (_) {}
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Gateway did not become ready for the cloud library tests.');
}

test('personal library persists playlists, favorites and history separately by account', async t => {
  const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'afrogo-library-test-'));
  const port = await unusedPort();
  const env = {
    ...process.env,
    PORT: String(port), NODE_ENV: 'development', DATA_DIR: path.join(temp, 'data'),
    JWT_SECRET: 'test-secret-' + 'x'.repeat(64),
    BOOTSTRAP_ADMIN_EMAIL: 'cloud-test@example.com',
    BOOTSTRAP_ADMIN_PASSWORD: 'test-password-very-secure-2026',
    AZURACAST_BASE_URL: '', AZURACAST_STATION_ID: '', AZURACAST_API_KEY: '',
    PUBLIC_API_BASE_URL: ''
  };
  const child = spawn(process.execPath, [path.join(__dirname, 'server.js')], { env, stdio: 'ignore' });
  t.after(async () => {
    child.kill('SIGTERM');
    await new Promise(resolve => setTimeout(resolve, 150));
    await fs.rm(temp, { recursive: true, force: true });
  });
  const base = `http://127.0.0.1:${port}`;
  await waitFor(`${base}/health`, child);
  const login = await fetch(`${base}/api/auth/login`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: 'cloud-test@example.com', password: 'test-password-very-secure-2026' })
  });
  assert.equal(login.status, 200);
  const loginData = await login.json();
  const { token, refreshToken } = loginData;
  assert.ok(refreshToken, 'login should issue a persistent refresh token');
  assert.ok(loginData.refreshExpiresIn >= 30 * 24 * 60 * 60, 'refresh token should last beyond app restarts');
  const refreshed = await fetch(`${base}/api/auth/refresh`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  });
  assert.equal(refreshed.status, 200);
  const refreshedData = await refreshed.json();
  assert.ok(refreshedData.token);
  assert.ok(refreshedData.refreshToken);
  const legacyUpgrade = await fetch(`${base}/api/auth/refresh`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ token: refreshedData.token })
  });
  assert.equal(legacyUpgrade.status, 200, 'old installed apps can upgrade an existing valid access token');
  const legacyUpgradeData = await legacyUpgrade.json();
  assert.ok(legacyUpgradeData.refreshToken);
  const headers = { authorization: `Bearer ${legacyUpgradeData.token}`, 'content-type': 'application/json' };
  const getLibrary = () => fetch(`${base}/api/me/library`, { headers });
  let response = await getLibrary();
  assert.equal(response.status, 200);
  let library = await response.json();
  assert.equal(library.initialized, false);

  response = await fetch(`${base}/api/me/library`, {
    method: 'PUT', headers,
    body: JSON.stringify({
      playlists: [{ id: 'pl_test_1', name: 'Mi playlist', description: 'Prueba', songIds: ['azura:157'], cover: '' }],
      favorites: ['azura:157'], savedTracks: ['azura:157'],
      recentTracks: [{ id: 'azura:157', title: 'Canción de prueba', artist: 'Artista', album: 'Álbum', genre: 'Pop', cover: '', playedAt: Date.now() }]
    })
  });
  assert.equal(response.status, 200);
  library = await response.json();
  assert.equal(library.playlists.length, 1);
  assert.deepEqual(library.favorites, ['azura:157']);
  assert.equal(library.recentTracks[0].title, 'Canción de prueba');

  // Create a second approved account and verify it cannot see the administrator's library.
  response = await fetch(`${base}/api/auth/register`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: 'second-user@example.com', password: 'second-user-secure-2026', name: 'Segundo usuario' })
  });
  assert.equal(response.status, 201);
  const pending = await response.json();
  response = await fetch(`${base}/api/admin/users/${pending.user.id}/approve`, { method: 'POST', headers });
  assert.equal(response.status, 200);
  const secondLogin = await fetch(`${base}/api/auth/login`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: 'second-user@example.com', password: 'second-user-secure-2026' })
  });
  assert.equal(secondLogin.status, 200);
  const secondToken = (await secondLogin.json()).token;
  const secondLibraryResponse = await fetch(`${base}/api/me/library`, { headers: { authorization: `Bearer ${secondToken}` } });
  const secondLibrary = await secondLibraryResponse.json();
  assert.equal(secondLibrary.playlists.length, 0);
  assert.deepEqual(secondLibrary.favorites, []);

  response = await fetch(`${base}/api/me/playlists`, {
    method: 'POST', headers,
    body: JSON.stringify({ playlist: { id: 'pl_test_1', name: 'Mi playlist editada', description: '', songIds: ['azura:157', 'azura:22'], cover: '' } })
  });
  assert.equal(response.status, 200);
  response = await fetch(`${base}/api/me/favorites`, {
    method: 'POST', headers, body: JSON.stringify({ trackId: 'azura:157', favorite: false })
  });
  assert.equal(response.status, 200);
  library = await (await getLibrary()).json();
  assert.equal(library.playlists[0].name, 'Mi playlist editada');
  assert.deepEqual(library.favorites, []);
  assert.deepEqual(library.savedTracks, []);

  response = await fetch(`${base}/api/me/playlists/pl_test_1`, { method: 'DELETE', headers });
  assert.equal(response.status, 200);
  library = await (await getLibrary()).json();
  assert.equal(library.playlists.length, 0);
});
