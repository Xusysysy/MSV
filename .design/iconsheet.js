// 图标集预览：从 res/drawable/ic_msv_*.xml 读取 pathData，拼成 SVG 网格并光栅化。
// 目的：让「实际发布的矢量资源」被真实渲染出来供人工核对，而不是只信手写坐标。
const fs = require('fs');
const path = require('path');
const sharp = require('sharp');

const dir = path.join(__dirname, '..', 'app', 'src', 'main', 'res', 'drawable');
const files = fs.readdirSync(dir)
  .filter(f => f.startsWith('ic_msv_') && f.endsWith('.xml') && !f.includes('launcher'))
  .sort();

const CELL = 76, PAD = 20, COLS = 6;
const ART = CELL - PAD * 2;
const scale = ART / 24;
const rows = Math.ceil(files.length / COLS);
const W = COLS * (CELL + 12) + 12;
const H = rows * (CELL + 12) + 12;

const tiles = files.map((f, i) => {
  const xml = fs.readFileSync(path.join(dir, f), 'utf8');
  const cx = 12 + (i % COLS) * (CELL + 12);
  const cy = 12 + Math.floor(i / COLS) * (CELL + 12);

  const paths = [...xml.matchAll(/<path\b([^>]*?)\/>/gs)].map(m => {
    const a = m[1];
    const d = (a.match(/android:pathData="([^"]*)"/) || [, ''])[1];
    const fillRaw = (a.match(/android:fillColor="([^"]*)"/) || [, ''])[1];
    const strokeRaw = (a.match(/android:strokeColor="([^"]*)"/) || [, ''])[1];
    const sw = (a.match(/android:strokeWidth="([^"]*)"/) || [, '2'])[1];
    const cap = (a.match(/android:strokeLineCap="([^"]*)"/) || [, 'butt'])[1];
    const join = (a.match(/android:strokeLineJoin="([^"]*)"/) || [, 'miter'])[1];
    const fill = (!fillRaw || fillRaw === '#00000000') ? 'none' : '#F5F7FF';
    const stroke = (strokeRaw && strokeRaw !== '#00000000') ? '#F5F7FF' : 'none';
    const swOut = stroke === 'none' ? 0 : sw;
    return `<path d="${d}" fill="${fill}" stroke="${stroke}" stroke-width="${swOut}" stroke-linecap="${cap}" stroke-linejoin="${join}"/>`;
  });

  const label = f.replace('ic_msv_', '').replace('.xml', '');
  const artOrigin = `translate(${(cx + PAD).toFixed(2)},${(cy + PAD).toFixed(2)}) scale(${scale.toFixed(4)})`;

  return `<rect x="${cx}" y="${cy}" width="${CELL}" height="${CELL}" rx="16" fill="#1A1F2E"/>` +
    `<g transform="${artOrigin}">${paths.join('')}</g>` +
    `<text x="${cx + CELL / 2}" y="${cy + CELL - 5}" font-size="10" fill="#7C8798" text-anchor="middle" font-family="monospace">${label}</text>`;
});

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">` +
  `<rect width="${W}" height="${H}" fill="#0B0E16"/>${tiles.join('')}</svg>`;

fs.writeFileSync(path.join(__dirname, 'icons.svg'), svg);
sharp(Buffer.from(svg), { density: 200 })
  .png()
  .toFile(path.join(__dirname, 'icons.png'))
  .then(() => console.log('OK', files.length, 'icons:', files.map(f => f.replace('ic_msv_', '').replace('.xml', '')).join(' ')))
  .catch(e => { console.error('FAIL:', e.message); process.exit(1); });
