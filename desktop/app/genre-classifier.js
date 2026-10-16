(function (root, factory) {
  const api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (root) root.AfrogoGenreClassifier = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  function text(value) {
    return String(value == null ? '' : value)
      .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
      .toLowerCase().replace(/&/g, ' and ').replace(/[^a-z0-9]+/g, ' ').trim();
  }

  const EMPTY_VALUES = new Set(['', 'unknown', 'none', 'null', 'n a', 'na', 'other', 'misc', 'various', 'sin categoria', 'sin genero', 'desconocido', 'not set', 'undefined', 'latin', 'latino', 'world', 'music', 'musica']);

  const RULES = [
    ['Cumbia', /\b(cumbia|chicha|tecnocumbia|cumbia villera|cumbia peruana|cumbia sonidera)\b/],
    ['Salsa', /\b(salsa|timba|son cubano|son montuno)\b/],
    ['Reguetón', /\b(reggaeton|regueton|reggaeton latino|latin reggaeton|perreo)\b/],
    ['Urbano', /\b(urbano|urban|latin trap|trap latino|trap|hip hop|hiphop|rap|dembow|drill|gangsta rap|latin urban)\b/],
    ['Electrónica', /\b(electronic|electronica|edm|house|deep house|techno|trance|dubstep|drum and bass|dnb|electro|dance music|synthwave|psytrance|hardstyle)\b/],
    ['Baladas', /\b(balada|baladas|ballad|ballads|romantica|romantico|romantic pop|latin ballad|bolero|balada pop)\b/],
    ['Merengue', /\b(merengue)\b/],
    ['Afrobeat', /\b(afrobeat|afrobeats|afro pop)\b/],
    ['Regional', /\b(regional mexicano|regional|banda sinaloense|banda|norteno|nortena|corridos tumbados|corridos|mariachi|ranchera|rancheras)\b/],
    ['Rock', /\b(rock|classic rock|alternative rock|indie rock|hard rock|metal|heavy metal|punk rock|punk|grunge|post punk|pop rock)\b/],
    ['Jazz', /\b(jazz|smooth jazz|bebop|blues)\b/],
    ['Pop', /\b(pop|k pop|kpop|indie pop|synth pop|latin pop|pop latino|dance pop)\b/],
    ['Clásica', /\b(classical|classica|orchestra|orchestral|opera)\b/],
    ['Folk', /\b(folk|folklore|acoustic|country|indie folk)\b/],
    ['R&B', /\b(r and b|rnb|rhythm and blues|soul|funk)\b/]
  ];

  const ARTIST_RULES = [
    ['Cumbia', ['grupo 5', 'grupo cinco', 'armonia 10', 'corazon serrano', 'agua marina', 'los mirlos', 'los destellos', 'grupo nectar', 'hermanos yaipen', 'la unica tropical', 'la bella luz', 'los caribenos', 'sonido 2000', 'amar azul', 'damas gratis', 'ke personajes', 'grupo nectar', 'grupo guinda', 'grupo alegria', 'aromonia 10', 'corazon serrano']],
    ['Salsa', ['marc anthony', 'hector lavoe', 'willie colon', 'willie colon', 'gilberto santa rosa', 'grupo niche', 'victor manuelle', 'frankie ruiz', 'jerry rivera', 'oscar d leon', 'el gran combo', 'ruben blades', 'tito nieves', 'maelo ruiz', 'la india', 'tony vega', 'celia cruz', 'joe arroyo', 'adolescentes orquesta', 'sonora carruseles', 'eddy herrera']],
    ['Reguetón', ['j balvin', 'daddy yankee', 'don omar', 'nicky jam', 'wisin y yandel', 'wisin', 'yandel', 'farruko', 'ozuna', 'maluma', 'rauw alejandro', 'myke towers', 'anuel aa', 'karol g', 'feid', 'jhayco', 'sech', 'lunay', 'de la ghetto', 'plan b', 'zion y lennox', 'ivy queen', 'tito el bambino', 'chencho corleone', 'yandel', 'jowell y randy', 'zion', 'lennox']],
    ['Urbano', ['bad bunny', 'arcangel', 'arcangel', 'eladio carrion', 'mora', 'alvaro diaz', 'yung beef', 'c tangana', 'trueno', 'nicki nicole', 'duki', 'paulo londra', 'bizarrap', 'santa fe klan', 'snoop dogg', 'eminem', 'drake', 'kendrick lamar', 'travis scott', '21 savage', 'central cee', '50 cent', 'jay z']],
    ['Electrónica', ['daft punk', 'david guetta', 'calvin harris', 'avicii', 'tiesto', 'martin garrix', 'skrillex', 'the chainsmokers', 'swedish house mafia', 'kygo', 'alan walker', 'deadmau5', 'armin van buuren', 'above and beyond', 'marshmello', 'chemical brothers', 'fatboy slim', 'afrojack', 'hardwell']],
    ['Baladas', ['sin bandera', 'reik', 'camila', 'rio roma', 'ricardo arjona', 'luis miguel', 'cristian castro', 'jose jose', 'los temerarios', 'grupo bryndis', 'noel schajris', 'jesse and joy', 'pandora', 'rocio durcal', 'alejandro fernandez', 'alejandro sanz', 'alex ubago', 'franco de vita', 'laura pausini', 'ana gabriel', 'emanuel']],
    ['Rock', ['arctic monkeys', 'coldplay', 'linkin park', 'queen', 'nirvana', 'metallica', 'soda stereo', 'mana', 'enanitos verdes', 'the beatles', 'imagine dragons', 'green day', 'foo fighters', 'paramore', 'the rolling stones', 'guns n roses', 'ac dc', 'red hot chili peppers', 'radiohead', 'the killers', 'pink floyd', 'the doors', 'heroes del silencio']],
    ['Pop', ['taylor swift', 'dua lipa', 'bruno mars', 'michael jackson', 'madonna', 'shakira', 'rihanna', 'katy perry', 'lady gaga', 'ed sheeran', 'justin bieber', 'ariana grande', 'billie eilish', 'sabrina carpenter', 'luis fonsi', 'camilo', 'morat', 'sebastian yatra', 'alex ponce']],
    ['Afrobeat', ['burna boy', 'wizkid', 'davido', 'tems', 'rema', 'ayra starr', 'fela kuti', 'asake']]
  ];

  function canonicalLabel(value) {
    const n = text(value);
    if (EMPTY_VALUES.has(n)) return '';
    for (const [canonical, re] of RULES) if (re.test(n)) return canonical;
    return '';
  }

  function classifyGenre(rawGenre, context = {}) {
    const raw = Array.isArray(rawGenre) ? rawGenre.join('; ') : String(rawGenre == null ? '' : rawGenre);
    const labels = raw.split(/[;,|]/).map(s => s.trim()).filter(Boolean);
    for (const label of labels) {
      const known = canonicalLabel(label);
      if (known) return { genre: known, source: 'metadata', confidence: 0.98 };
    }
    const meaningful = labels.find(label => !EMPTY_VALUES.has(text(label)));
    const title = String(context.title || '');
    const artist = String(context.artist || '');
    const album = String(context.album || '');
    const path = String(context.path || context.filename || '');
    const contextText = [title, album, path].join(' ');
    const textGenre = canonicalLabel(contextText);
    if (textGenre) return { genre: textGenre, source: 'title-album', confidence: 0.78 };
    const artistText = text(artist);
    for (const [genre, names] of ARTIST_RULES) {
      if (names.some(name => artistText.includes(text(name)))) return { genre, source: 'artist-inference', confidence: 0.72 };
    }
    // Keep a real, unrecognized tag instead of discarding information.
    if (meaningful) return { genre: meaningful.slice(0, 48), source: 'metadata-unmapped', confidence: 0.55 };
    return { genre: 'Sin categoría', source: 'unknown', confidence: 0 };
  }

  const CATEGORY_BUCKETS = {
    'Urbano': new Set(['urbano', 'regueton', 'reggaeton', 'trap', 'hip hop', 'rap', 'dembow', 'drill', 'urban', 'latin trap']),
    'Regueton': new Set(['regueton', 'reggaeton', 'perreo']),
    'Salsa': new Set(['salsa', 'timba', 'son cubano', 'son montuno']),
    'Cumbia': new Set(['cumbia', 'chicha', 'tecnocumbia', 'cumbia villera']),
    'Baladas': new Set(['baladas', 'balada', 'ballad', 'ballads', 'romantica', 'romantico', 'bolero']),
    'Electronica': new Set(['electronica', 'electronic', 'edm', 'house', 'techno', 'trance', 'dubstep', 'drum and bass', 'dnb', 'electro', 'dance music', 'synthwave']),
    'Rock': new Set(['rock', 'metal', 'punk', 'grunge']),
    'Pop': new Set(['pop', 'k pop', 'kpop']),
    'Afrobeat': new Set(['afrobeat', 'afrobeats']),
    'Merengue': new Set(['merengue']),
    'Jazz': new Set(['jazz', 'blues']),
    'Regional': new Set(['regional', 'banda', 'norteno', 'corridos', 'mariachi', 'ranchera']),
    'Clasica': new Set(['clasica', 'classical', 'opera']),
    'Folk': new Set(['folk', 'folklore', 'country'])
  };

  function matchesCategory(rawGenre, category, context = {}) {
    const wanted = text(category);
    const result = classifyGenre(rawGenre, context);
    const found = text(result.genre);
    if (!found || found === 'sin categoria') return false;
    const bucket = CATEGORY_BUCKETS[Object.keys(CATEGORY_BUCKETS).find(k => text(k) === wanted)] || new Set([wanted]);
    if (bucket.has(found)) return true;
    // When an unmapped label contains a recognized genre alias, include it in its bucket.
    for (const value of bucket) if (found === value || found.includes(value)) return true;
    return false;
  }

  return { classifyGenre, matchesCategory, normalizeGenreText: text };
});
