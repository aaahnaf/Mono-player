import { mkdirSync, copyFileSync } from 'node:fs';
mkdirSync('www', { recursive: true });
copyFileSync('src/index.html', 'www/index.html');
copyFileSync('node_modules/three/build/three.module.min.js', 'www/three.module.min.js');
console.log('www/ ready');
