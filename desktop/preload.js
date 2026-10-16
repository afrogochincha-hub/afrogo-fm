const { contextBridge, ipcRenderer } = require('electron');
const fs = require('node:fs');
const path = require('node:path');

let configuredApiBaseUrl = String(process.env.AFROGO_API_BASE_URL || '').trim();
if (!configuredApiBaseUrl) {
  try {
    const configPath = path.join(__dirname, 'app-config.json');
    const cfg = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    configuredApiBaseUrl = String(cfg.apiBaseUrl || '').trim();
  } catch (_) { /* the renderer will show a configuration notice */ }
}
configuredApiBaseUrl = configuredApiBaseUrl.replace(/\/+$/, '');

contextBridge.exposeInMainWorld('afrogo', {
  config: { apiBaseUrl: configuredApiBaseUrl, allowInsecureHttp: process.env.AFROGO_ALLOW_INSECURE_HTTP === '1' },
  listTracks: () => ipcRenderer.invoke('library:list'),
  saveTrack: (track) => ipcRenderer.invoke('library:save', track),
  updateTrack: (input) => ipcRenderer.invoke('library:update', input),
  deleteTrack: (id) => ipcRenderer.invoke('library:delete', id),
  scanAlbums: () => ipcRenderer.invoke('library:scanAlbums')
});
