import { mkdirSync, copyFileSync, existsSync } from 'node:fs';

mkdirSync('www', { recursive: true });
copyFileSync('src/index.html', 'www/index.html');

// three ships an ESM build (three.module.js); the .min variant does not exist.
const candidates = [
  'node_modules/three/build/three.module.js',
  'node_modules/three/build/three.module.min.js',
];
const found = candidates.find((p) => existsSync(p));
if (!found) throw new Error('three.js build not found — run `npm install` first.');
copyFileSync(found, 'www/three.module.js');
console.log('www/ ready');
