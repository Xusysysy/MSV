const sharp = require('sharp');
const fs = require('fs');
const [ , , inFile, outFile, size ] = process.argv;
const s = parseInt(size || '256', 10);
sharp(inFile, { density: 384 })
  .resize(s, s)
  .png()
  .toFile(outFile)
  .then(() => console.log('OK ->', outFile))
  .catch(e => { console.error('FAIL:', e.message); process.exit(1); });
