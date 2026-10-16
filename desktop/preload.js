const { contextBridge, ipcRenderer } = require('electron');

// Electron's sandboxed preload receives public runtime settings via additionalArguments.
// Do not load fs/path here: sandboxed preloads have a restricted Node.js require().
function readArgument(prefix) {
  const value = process.argv.find((arg) => arg.startsWith(prefix));
  return value ? value.slice(prefix.length) : '';
}
let configuredApiBaseUrl = String(
  readArgument('--afrogo-api-base-url=') || process.env.AFROGO_API_BASE_URL || ''
).trim().replace(/\/+$/, '');
const allowInsecureHttp = readArgument('--afrogo-allow-insecure-http=') === '1' || process.env.AFROGO_ALLOW_INSECURE_HTTP === '1';

contextBridge.exposeInMainWorld('afrogo', {
  config: { apiBaseUrl: configuredApiBaseUrl, allowInsecureHttp },
  listTracks: () => ipcRenderer.invoke('library:list'),
  saveTrack: (track) => ipcRenderer.invoke('library:save', track),
  updateTrack: (input) => ipcRenderer.invoke('library:update', input),
  deleteTrack: (id) => ipcRenderer.invoke('library:delete', id),
  scanAlbums: () => ipcRenderer.invoke('library:scanAlbums')
});
