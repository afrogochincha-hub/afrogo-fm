'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { classifyGenre, matchesCategory } = require('./genre-classifier');

test('normaliza géneros conocidos de AzuraCast', () => {
  assert.equal(classifyGenre('Reggaeton').genre, 'Reguetón');
  assert.equal(classifyGenre('Salsa romántica').genre, 'Salsa');
  assert.equal(classifyGenre('Cumbia peruana').genre, 'Cumbia');
  assert.equal(classifyGenre('EDM').genre, 'Electrónica');
});

test('infiere géneros cuando el tag falta', () => {
  assert.equal(classifyGenre('Sin categoría', { artist: 'Bad Bunny', title: 'Moscow Mule' }).genre, 'Urbano');
  assert.equal(classifyGenre('', { artist: 'Daft Punk', title: 'Give Life Back to Music' }).genre, 'Electrónica');
  assert.equal(classifyGenre('', { artist: 'Grupo 5', title: 'Motor y Motivo' }).genre, 'Cumbia');
  assert.equal(classifyGenre('', { artist: 'Artista desconocido', title: 'Cumbia Mix' }).genre, 'Cumbia');
});

test('no inventa un género si no hay suficientes datos', () => {
  const result = classifyGenre('Sin categoría', { artist: 'Artista X', title: 'Pista 01', album: '' });
  assert.equal(result.genre, 'Sin categoría');
  assert.equal(result.source, 'unknown');
});

test('las categorías agrupan alias compatibles', () => {
  assert.equal(matchesCategory('Reguetón', 'Urbano'), true);
  assert.equal(matchesCategory('Trap', 'Urbano'), true);
  assert.equal(matchesCategory('Salsa romántica', 'Salsa'), true);
  assert.equal(matchesCategory('Chicha', 'Cumbia'), true);
  assert.equal(matchesCategory('EDM', 'Electrónica'), true);
  assert.equal(matchesCategory('Rock', 'Salsa'), false);
});
