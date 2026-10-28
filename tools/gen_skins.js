// 生成芙宁娜 / 胡桃 的 64x64 Minecraft 皮肤（Alex 细臂）, 以及刷怪蛋贴图、预览图。
// 纯 Node 实现，不依赖任何三方库。运行: node tools/gen_skins.js
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

/* ---------------- PNG 编码 ---------------- */
const crcTable = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function writePng(file, w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const png = Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, png);
  console.log('wrote', path.relative(process.cwd(), file), png.length + 'B');
}

/* ---------------- 画布 ---------------- */
function hex(c) {
  const s = c.replace('#', '');
  return [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16), 255];
}
class Canvas {
  constructor(w, h) { this.w = w; this.h = h; this.buf = Buffer.alloc(w * h * 4); }
  px(x, y, c) {
    x |= 0; y |= 0;
    if (x < 0 || y < 0 || x >= this.w || y >= this.h || !c) return;
    const o = (y * this.w + x) * 4;
    this.buf[o] = c[0]; this.buf[o + 1] = c[1]; this.buf[o + 2] = c[2]; this.buf[o + 3] = c[3] === undefined ? 255 : c[3];
  }
  rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.px(x + i, y + j, c); }
  clear(x, y, w, h) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) { const o = ((y + j) * this.w + x + i) * 4; this.buf[o + 3] = 0; } }
  copyFrom(src, sx, sy, w, h, dx, dy) {
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
      const so = ((sy + j) * src.w + sx + i) * 4;
      this.px(dx + i, dy + j, [src.buf[so], src.buf[so + 1], src.buf[so + 2], src.buf[so + 3]]);
    }
  }
}

/* ------------- 皮肤 UV（64x64, Alex 细臂） ------------- */
const FACES = ['top', 'bottom', 'right', 'front', 'left', 'back'];
const PART = {
  head: { front: [8, 8, 8, 8], right: [0, 8, 8, 8], back: [24, 8, 8, 8], left: [16, 8, 8, 8], top: [8, 0, 8, 8], bottom: [16, 0, 8, 8] },
  body: { front: [20, 20, 8, 12], right: [16, 20, 4, 12], back: [32, 20, 8, 12], left: [28, 20, 4, 12], top: [20, 16, 8, 4], bottom: [28, 16, 8, 4] },
  rArm: { front: [44, 20, 3, 12], right: [40, 20, 3, 12], back: [50, 20, 3, 12], left: [47, 20, 3, 12], top: [44, 16, 3, 4], bottom: [47, 16, 3, 4] },
  lArm: { front: [36, 52, 3, 12], right: [32, 52, 3, 12], back: [42, 52, 3, 12], left: [39, 52, 3, 12], top: [36, 48, 3, 4], bottom: [39, 48, 3, 4] },
  rLeg: { front: [4, 20, 4, 12], right: [0, 20, 4, 12], back: [12, 20, 4, 12], left: [8, 20, 4, 12], top: [4, 16, 4, 4], bottom: [8, 16, 4, 4] },
  lLeg: { front: [20, 52, 4, 12], right: [16, 52, 4, 12], back: [28, 52, 4, 12], left: [24, 52, 4, 12], top: [20, 48, 4, 4], bottom: [24, 48, 4, 4] },
};
// 帽子 overlay：head 各面 +32x
const HAT = Object.fromEntries(FACES.map(f => [f, [PART.head[f][0] + 32, PART.head[f][1], PART.head[f][2], PART.head[f][3]]]));

function faceFill(cv, part, face, color) { const [x, y, w, h] = part[face]; cv.rect(x, y, w, h, color); }
function fp(cv, part, face, lx, ly, color) { const [x, y, w, h] = part[face]; if (lx < 0 || ly < 0 || lx >= w || ly >= h) return; cv.px(x + lx, y + ly, color); }

/* ---------------- 芙宁娜 ---------------- */
function drawFurina() {
  const cv = new Canvas(64, 64);
  const C = {
    skin: hex('#f7e3d9'), skinShade: hex('#e8cabe'), blush: hex('#edaeb0'),
    hairHi: hex('#f8fbfe'), hair: hex('#e4eef8'), hairShade: hex('#cadcec'), hairTip: hex('#9dc3e5'), hairTip2: hex('#7ba9d6'),
    hat: hex('#1e2a45'), hatHi: hex('#33455f'), band: hex('#5a8fd1'), gold: hex('#e6c374'),
    coat: hex('#eff4fa'), coatShade: hex('#d3dfec'), vest: hex('#26324d'), ribbon: hex('#4f7fc4'), blue: hex('#6fa6de'),
    glove: hex('#e9eff7'), shoe: hex('#202b46'),
    white: hex('#ffffff'), iris: hex('#63b6d9'), irisDark: hex('#2c6b93'), pupil: hex('#1c3850'), mouth: hex('#cf8d95'),
  };
  // ---- 头 ----
  for (const f of FACES) faceFill(cv, PART.head, f, C.hair);
  // 头顶
  cv.rect(8, 0, 8, 8, C.hairHi);
  cv.rect(9, 2, 5, 4, C.hair);
  fp(cv, PART.head, 'top', 2, 5, C.hairShade); fp(cv, PART.head, 'top', 5, 1, C.hairShade);
  // 右脸（角色右侧 = 观察者左侧）
  const side = (face, flip) => {
    const [x, y] = PART.head[face];
    cv.rect(x, y, 8, 4, C.hair);
    cv.rect(x, y, 8, 1, C.hairHi);
    // 鬓发渐层
    for (let i = 0; i < 8; i++) {
      const ly = 4 + (i % 3);
      cv.px(x + (flip ? 7 - i : i), y + ly, i % 2 ? C.hairShade : C.hair);
    }
    cv.rect(x + (flip ? 5 : 0), y + 4, 3, 4, C.hairTip);
    cv.rect(x + (flip ? 6 : 1), y + 5, 1, 3, C.hairTip2);
    // 耳侧皮肤
    cv.rect(x + (flip ? 0 : 5), y + 5, 2, 2, C.skinShade);
  };
  side('right', false);
  side('left', true);
  // 后脑：长发渐变
  {
    const [x, y] = PART.head.back;
    cv.rect(x, y, 8, 8, C.hair);
    cv.rect(x, y, 8, 2, C.hairHi);
    for (let i = 0; i < 8; i++) {
      const len = 4 + ((i * 5) % 4);
      cv.rect(x + i, y + len, 1, 8 - len, i % 3 === 0 ? C.hairTip2 : C.hairTip);
    }
    cv.rect(x + 1, y + 1, 2, 1, C.hairShade);
    cv.rect(x + 5, y + 2, 2, 1, C.hairShade);
  }
  // 底面
  faceFill(cv, PART.head, 'bottom', C.hairShade);
  // 正面
  {
    const [x, y] = PART.head.front;
    // 刘海
    cv.rect(x, y, 8, 3, C.hair);
    cv.rect(x, y, 8, 1, C.hairHi);
    cv.rect(x + 3, y + 2, 2, 1, C.hairTip);
    cv.rect(x + 1, y + 3, 1, 1, C.hairTip2);
    cv.rect(x + 6, y + 3, 1, 1, C.hairTip2);
    // 两侧发丝
    cv.rect(x, y + 3, 1, 5, C.hairTip);
    cv.rect(x + 7, y + 3, 1, 5, C.hairTip);
    // 脸
    cv.rect(x + 1, y + 3, 6, 5, C.skin);
    fp(cv, PART.head, 'front', 0, 7, C.skinShade); fp(cv, PART.head, 'front', 7, 7, C.skinShade);
    // 眼
    cv.px(x + 1, y + 4, C.irisDark); cv.px(x + 2, y + 4, C.iris); cv.px(x + 2, y + 5, C.pupil);
    cv.px(x + 5, y + 4, C.iris); cv.px(x + 6, y + 4, C.irisDark); cv.px(x + 5, y + 5, C.pupil);
    cv.px(x + 1, y + 5, C.white);
    // 眉/腮红/嘴
    cv.px(x + 4, y + 4, C.skinShade);
    fp(cv, PART.head, 'front', 0, 6, C.blush); fp(cv, PART.head, 'front', 7, 6, C.blush);
    cv.px(x + 3, y + 6, C.mouth); cv.px(x + 4, y + 6, C.mouth);
  }
  // ---- 帽子 overlay ----
  for (const f of FACES) {
    if (f === 'bottom') continue;
    const [x, y, w, h] = HAT[f];
    cv.clear(x, y, w, h);
  }
  {
    const [x, y] = HAT.top;
    cv.rect(x, y, 8, 8, C.hat);
    cv.rect(x + 1, y + 1, 6, 5, C.hatHi);
    cv.rect(x + 1, y + 1, 6, 1, C.band);
    cv.rect(x + 3, y + 3, 2, 2, C.gold);
  }
  for (const f of ['front', 'back', 'left', 'right']) {
    const [x, y] = HAT[f];
    cv.rect(x, y, 8, 3, C.hat);
    cv.rect(x, y, 8, 1, C.hatHi);
    cv.rect(x + (f === 'front' ? 3 : 1), y + 1, 2, 2, C.gold);
    cv.rect(x, y + 3, 8, 1, C.band);
    cv.rect(x, y + 4, 8, 1, C.hat);
  }
  // ---- 身体 ----
  faceFill(cv, PART.body, 'front', C.coat);
  faceFill(cv, PART.body, 'back', C.coat);
  faceFill(cv, PART.body, 'left', C.coat);
  faceFill(cv, PART.body, 'right', C.coat);
  faceFill(cv, PART.body, 'top', C.coatShade);
  faceFill(cv, PART.body, 'bottom', C.coatShade);
  {
    const [x, y] = PART.body.front;
    // 领口
    cv.rect(x, y, 8, 2, C.coatShade);
    cv.rect(x + 3, y, 2, 3, C.white);
    cv.px(x + 3, y + 1, C.ribbon); cv.px(x + 4, y + 1, C.ribbon);
    // 蓝马甲 V 领
    cv.rect(x + 1, y + 2, 2, 5, C.vest);
    cv.rect(x + 5, y + 2, 2, 5, C.vest);
    cv.rect(x + 3, y + 3, 2, 4, C.coat);
    cv.px(x + 4, y + 4, C.gold); cv.px(x + 4, y + 6, C.gold);
    // 腰带
    cv.rect(x, y + 7, 8, 1, C.blue);
    cv.px(x + 3, y + 7, C.gold); cv.px(x + 4, y + 7, C.gold);
    // 下摆
    cv.rect(x, y + 8, 8, 4, C.coat);
    cv.rect(x, y + 10, 8, 2, C.coatShade);
    cv.rect(x + 1, y + 11, 6, 1, C.blue);
    // 背后下摆的水纹
  }
  {
    const [x, y] = PART.body.back;
    cv.rect(x, y, 8, 2, C.coatShade);
    cv.rect(x + 1, y + 2, 6, 2, C.white);
    cv.rect(x, y + 4, 8, 5, C.blue);
    cv.rect(x, y + 9, 8, 3, C.vest);
    for (let i = 0; i < 4; i++) cv.rect(x + 1 + i * 2, y + 5 + (i % 2), 1, 2, C.white);
    cv.px(x + 3, y + 2, C.gold);
  }
  for (const f of ['left', 'right']) {
    const [x, y] = PART.body[f];
    cv.rect(x, y, 4, 7, C.vest);
    cv.rect(x, y + 7, 4, 5, C.coat);
    cv.rect(x, y + 9, 4, 3, C.coatShade);
  }
  // ---- 手臂 ----
  for (const key of ['rArm', 'lArm']) {
    for (const f of FACES) faceFill(cv, PART[key], f, C.coat);
    const [x, y] = PART[key].front;
    cv.rect(x, y, 3, 2, C.coatShade);
    cv.rect(x, y + 2, 3, 2, C.blue);
    cv.px(x + 1, y + 2, C.gold);
    cv.rect(x, y + 4, 3, 5, C.glove);
    cv.rect(x, y + 9, 3, 1, C.glove);
    cv.rect(x, y + 10, 3, 2, C.skin);
    // 袖口/手套背面
    const [bx, by] = PART[key].back;
    cv.rect(bx, by, 3, 4, C.coat);
    cv.rect(bx, by + 4, 3, 5, C.glove);
    cv.rect(bx, by + 10, 3, 2, C.skin);
    for (const f of ['left', 'right']) { const [sx, sy] = PART[key][f]; cv.rect(sx, sy, 3, 4, C.coat); cv.rect(sx, sy + 4, 3, 6, C.glove); cv.rect(sx, sy + 10, 3, 2, C.skin); }
    faceFill(cv, PART[key], 'top', C.coatShade);
    faceFill(cv, PART[key], 'bottom', C.skinShade);
  }
  // ---- 腿 ----
  for (const key of ['rLeg', 'lLeg']) {
    for (const f of FACES) faceFill(cv, PART[key], f, C.white);
    const [x, y] = PART[key].front;
    cv.rect(x, y, 4, 9, C.white);
    cv.rect(x, y, 1, 9, C.coatShade);
    cv.rect(x, y + 9, 4, 3, C.shoe);
    cv.px(x + 1, y + 9, C.gold); cv.px(x + 2, y + 9, C.gold);
    const [bx, by] = PART[key].back;
    cv.rect(bx, by, 4, 9, C.white);
    cv.rect(bx, by + 9, 4, 3, C.shoe);
    for (const f of ['left', 'right']) { const [sx, sy] = PART[key][f]; cv.rect(sx, sy, 4, 9, C.white); cv.rect(sx, sy + 7, 4, 2, C.coatShade); cv.rect(sx, sy + 9, 4, 3, C.shoe); }
    faceFill(cv, PART[key], 'top', C.white);
    faceFill(cv, PART[key], 'bottom', C.shoe);
  }
  return cv;
}

/* ---------------- 胡桃 ---------------- */
function drawHutao() {
  const cv = new Canvas(64, 64);
  const C = {
    skin: hex('#f7e1d3'), skinShade: hex('#e8c6b4'), blush: hex('#e9a99e'),
    hair: hex('#3b2434'), hairHi: hex('#563a4c'), hairShade: hex('#2a1826'), tip: hex('#a93a41'), tipHi: hex('#c95454'),
    hat: hex('#221a25'), hatHi: hex('#3a2a3d'), band: hex('#8a3038'), gold: hex('#d9b365'), flower: hex('#f07b83'),
    dress: hex('#211a24'), dressHi: hex('#332834'), red: hex('#983440'), redHi: hex('#bc4e52'), white: hex('#f0eae0'),
    glove: hex('#2c2231'), shoe: hex('#17121a'),
    white2: hex('#ffffff'), iris: hex('#cf565b'), irisDark: hex('#7e2b32'), pupil: hex('#4d1820'), mouth: hex('#b35a60'),
  };
  // ---- 头 ----
  for (const f of FACES) faceFill(cv, PART.head, f, C.hair);
  cv.rect(8, 0, 8, 8, C.hairHi);
  cv.rect(9, 2, 5, 4, C.hair);
  cv.px(8 + 2, 5, C.hairShade); cv.px(8 + 6, 1, C.hairShade);
  const side = (face, flip) => {
    const [x, y] = PART.head[face];
    cv.rect(x, y, 8, 4, C.hair);
    cv.rect(x, y, 8, 1, C.hairHi);
    cv.rect(x + (flip ? 4 : 1), y + 4, 3, 3, C.hairShade);
    cv.rect(x + (flip ? 5 : 0), y + 5, 2, 3, C.hair);
    cv.rect(x + (flip ? 1 : 6), y + 5, 1, 3, C.tip);
    cv.rect(x + (flip ? 0 : 5), y + 7, 3, 1, C.tipHi);
  };
  side('right', false);
  side('left', true);
  {
    const [x, y] = PART.head.back;
    cv.rect(x, y, 8, 8, C.hair);
    cv.rect(x, y, 8, 2, C.hairHi);
    cv.rect(x + 1, y + 1, 2, 1, C.hairShade);
    cv.rect(x + 5, y + 3, 2, 1, C.hairShade);
    for (let i = 0; i < 8; i++) if (i % 3 !== 1) cv.rect(x + i, y + 6, 1, 2, C.tip);
  }
  faceFill(cv, PART.head, 'bottom', C.hairShade);
  {
    const [x, y] = PART.head.front;
    cv.rect(x, y, 8, 3, C.hair);
    cv.rect(x, y, 8, 1, C.hairHi);
    cv.rect(x + 2, y + 2, 2, 1, C.hairShade);
    cv.rect(x + 5, y + 2, 1, 1, C.hairShade);
    cv.rect(x, y + 3, 1, 1, C.tip);
    cv.rect(x + 7, y + 3, 1, 1, C.tip);
    cv.rect(x + 1, y + 3, 6, 5, C.skin);
    fp(cv, PART.head, 'front', 0, 7, C.skinShade); fp(cv, PART.head, 'front', 7, 7, C.skinShade);
    // 梅花眼：红瞳 + 白点
    cv.px(x + 1, y + 4, C.irisDark); cv.px(x + 2, y + 4, C.iris); cv.px(x + 1, y + 5, C.pupil);
    cv.px(x + 5, y + 4, C.iris); cv.px(x + 6, y + 4, C.irisDark); cv.px(x + 6, y + 5, C.pupil);
    cv.px(x + 2, y + 4, C.white2);
    cv.px(x + 4, y + 4, C.skinShade);
    fp(cv, PART.head, 'front', 0, 6, C.blush); fp(cv, PART.head, 'front', 7, 6, C.blush);
    cv.px(x + 3, y + 6, C.mouth); cv.px(x + 4, y + 6, C.mouth);
    cv.px(x + 4, y + 7, C.skinShade);
  }
  // ---- 帽子 ----
  for (const f of FACES) if (f !== 'bottom') { const [x, y, w, h] = HAT[f]; cv.clear(x, y, w, h); }
  {
    const [x, y] = HAT.top;
    cv.rect(x, y, 8, 8, C.hat);
    cv.rect(x + 1, y + 1, 6, 5, C.hatHi);
    cv.rect(x + 1, y + 1, 6, 1, C.band);
    cv.rect(x + 3, y + 4, 2, 2, C.flower);
    cv.px(x + 3, y + 4, C.gold); cv.px(x + 4, y + 5, C.gold);
  }
  for (const f of ['front', 'back', 'left', 'right']) {
    const [x, y] = HAT[f];
    cv.rect(x, y, 8, 3, C.hat);
    cv.rect(x, y, 8, 1, C.hatHi);
    cv.rect(x, y + 2, 8, 1, C.band);
    cv.rect(x, y + 3, 8, 1, C.hat);
    // 帽子上的梅花
    const fx = f === 'front' ? 1 : f === 'back' ? 6 : 3;
    cv.rect(x + fx, y + 1, 2, 2, C.flower);
    cv.px(x + fx, y + 1, C.gold);
  }
  // ---- 身体 ----
  faceFill(cv, PART.body, 'front', C.dress);
  faceFill(cv, PART.body, 'back', C.dress);
  for (const f of ['left', 'right']) faceFill(cv, PART.body, f, C.dress);
  faceFill(cv, PART.body, 'top', C.dressHi);
  faceFill(cv, PART.body, 'bottom', C.dressHi);
  {
    const [x, y] = PART.body.front;
    cv.rect(x, y, 8, 2, C.dressHi);
    cv.rect(x + 3, y, 2, 2, C.white);
    cv.px(x + 3, y + 1, C.gold); cv.px(x + 4, y + 1, C.gold);
    // 红色衣缘 + 金色滚边
    cv.rect(x + 1, y + 2, 1, 6, C.red);
    cv.rect(x + 6, y + 2, 1, 6, C.red);
    cv.rect(x + 3, y + 2, 2, 1, C.redHi);
    cv.rect(x + 3, y + 3, 2, 4, C.dress);
    cv.px(x + 4, y + 4, C.gold); cv.px(x + 4, y + 6, C.gold);
    cv.rect(x, y + 7, 8, 1, C.red);
    cv.px(x + 2, y + 7, C.gold); cv.px(x + 5, y + 7, C.gold);
    // 下摆
    cv.rect(x, y + 8, 8, 4, C.red);
    cv.rect(x, y + 8, 8, 1, C.redHi);
    for (let i = 0; i < 4; i++) cv.rect(x + i * 2 + 1, y + 10, 1, 2, C.gold);
  }
  {
    const [x, y] = PART.body.back;
    // 双马尾长发铺在背部
    cv.rect(x, y, 8, 2, C.dressHi);
    cv.rect(x, y + 2, 8, 10, C.dress);
    cv.rect(x, y + 2, 2, 9, C.hair);
    cv.rect(x + 6, y + 2, 2, 9, C.hair);
    cv.rect(x, y + 5, 1, 6, C.hairHi);
    cv.rect(x + 7, y + 5, 1, 6, C.hairHi);
    cv.rect(x, y + 10, 2, 2, C.tip);
    cv.rect(x + 6, y + 10, 2, 2, C.tip);
    cv.rect(x, y + 11, 2, 1, C.tipHi);
    cv.rect(x + 6, y + 11, 2, 1, C.tipHi);
    // 腰部红带
    cv.rect(x + 2, y + 7, 4, 1, C.red);
    cv.rect(x + 3, y + 8, 2, 2, C.gold);
  }
  for (const f of ['left', 'right']) {
    const [x, y] = PART.body[f];
    cv.rect(x, y, 4, 2, C.dressHi);
    cv.rect(x, y + 2, 4, 6, C.dress);
    cv.rect(x, y + 5, 4, 1, C.red);
    cv.rect(x, y + 8, 4, 4, C.red);
  }
  // ---- 手臂 ----
  for (const key of ['rArm', 'lArm']) {
    for (const f of FACES) faceFill(cv, PART[key], f, C.dress);
    for (const f of ['front', 'back', 'left', 'right']) {
      const [x, y] = PART[key][f];
      cv.rect(x, y, 3, 5, C.dress);
      cv.rect(x, y + 5, 3, 1, C.red);
      cv.rect(x, y + 6, 3, 4, C.glove);
      cv.rect(x, y + 10, 3, 2, C.glove);
      cv.px(x + 1, y + 5, C.gold);
    }
    faceFill(cv, PART[key], 'top', C.dressHi);
    faceFill(cv, PART[key], 'bottom', C.glove);
  }
  // ---- 腿 ----
  for (const key of ['rLeg', 'lLeg']) {
    for (const f of FACES) faceFill(cv, PART[key], f, C.dress);
    for (const f of ['front', 'back', 'left', 'right']) {
      const [x, y] = PART[key][f];
      cv.rect(x, y, 4, 7, C.dress);
      cv.rect(x, y + 7, 4, 3, C.shoe);
      cv.rect(x, y + 9, 4, 1, C.red);
      if (f === 'front') { cv.px(x + 1, y + 8, C.gold); cv.px(x + 2, y + 8, C.gold); }
    }
    faceFill(cv, PART[key], 'top', C.dressHi);
    faceFill(cv, PART[key], 'bottom', C.shoe);
  }
  return cv;
}

/* ---------------- 预览 ---------------- */
function preview(cv, file) {
  const S = 12;
  const W = 96, H = 40;
  const out = new Canvas(W * S, H * S);
  out.rect(0, 0, out.w, out.h, [245, 243, 238, 255]);
  const put = (rect, dx, dy) => {
    const [x, y, w, h] = rect;
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
      const o = ((y + j) * cv.w + x + i) * 4;
      if (!cv.buf[o + 3]) continue;
      out.rect((dx + i) * S, (dy + j) * S, S, S, [cv.buf[o], cv.buf[o + 1], cv.buf[o + 2], 255]);
    }
  };
  const view = (ox, headFace, bodyFace, armFace, legFace, hatFace) => {
    // 头、身体、手臂、腿（腿在身体下方）
    put(PART.rLeg[legFace], ox + 4, 22);
    put(PART.lLeg[legFace], ox + 8, 22);
    put(PART.body[bodyFace], ox + 4, 10);
    put(PART.rArm[armFace], ox + 1, 10);
    put(PART.lArm[armFace], ox + 12, 10);
    put(PART.head[headFace], ox + 4, 2);
    if (hatFace) put(HAT[hatFace], ox + 4, 2);
  };
  view(2, 'front', 'front', 'front', 'front', 'front');
  view(24, 'back', 'back', 'back', 'back', 'back');
  view(46, 'right', 'right', 'right', 'right', 'right');
  view(68, 'left', 'left', 'left', 'left', 'left');
  writePng(file, out.w, out.h, out.buf);
}

/* ---------------- 刷怪蛋 ---------------- */
function egg(file, palette, icon) {
  const cv = new Canvas(16, 16);
  const eggPixels = [
    '................',
    '.....######.....',
    '...##......##...',
    '..#..........#..',
    '..#..........#..',
    '.#....#...#...#.',
    '.#...#..#..#..#.',
    '.#..#.#.##.#..#.',
    '.#..#.#..#.#..#.',
    '.#...#....#...#.',
    '..#....##....#..',
    '..#..........#..',
    '...##......##...',
    '.....######.....',
    '................',
    '................',
  ];
  // 蛋形：更细腻的轮廓
  const rows = 16;
  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < 16; x++) {
      const dx = (x - 7.5) / 5.6;
      const dy = (y - 8.5) / 6.4;
      const d = dx * dx + dy * dy;
      if (d < 1) {
        let c = palette.base;
        if (dx * 1.7 + dy * 1.0 < -0.55) c = palette.hi;
        if (dx * 1.2 + dy * 1.3 > 0.9) c = palette.shade;
        cv.px(x, y, c);
      }
    }
  }
  // 高光
  cv.rect(5, 4, 2, 1, palette.glow);
  cv.rect(5, 5, 1, 1, palette.glow);
  // 主题花纹
  for (const [x, y, c] of icon) cv.px(x, y, c);
  writePng(file, 16, 16, cv.buf);
}

/* ---------------- 输出 ---------------- */
const root = path.resolve(__dirname, '..');
const entityDir = path.join(root, 'src/main/resources/assets/xinkun_genshin/textures/entity');
const itemDir = path.join(root, 'src/main/resources/assets/xinkun_genshin/textures/item');
const previewDir = path.join(root, 'preview');

const furina = drawFurina();
const hutao = drawHutao();
writePng(path.join(entityDir, 'furina.png'), 64, 64, furina.buf);
writePng(path.join(entityDir, 'hu_tao.png'), 64, 64, hutao.buf);
preview(furina, path.join(previewDir, 'furina_preview.png'));
preview(hutao, path.join(previewDir, 'hu_tao_preview.png'));

const F = {
  base: hex('#e9f1f9'), hi: hex('#ffffff'), shade: hex('#b9d3e8'), glow: hex('#ffffff'),
};
const H = {
  base: hex('#3a2a3a'), hi: hex('#5d465c'), shade: hex('#221a25'), glow: hex('#c9a7c0'),
};
egg(path.join(itemDir, 'furina_spawn_egg.png'), F, [
  [4, 7, hex('#5a8fd1')], [6, 9, hex('#5a8fd1')], [9, 6, hex('#5a8fd1')], [10, 9, hex('#5a8fd1')],
  [7, 4, hex('#26324d')], [8, 10, hex('#26324d')], [5, 11, hex('#e6c374')], [10, 5, hex('#e6c374')],
]);
egg(path.join(itemDir, 'hu_tao_spawn_egg.png'), H, [
  [4, 7, hex('#a93a41')], [6, 9, hex('#a93a41')], [9, 6, hex('#a93a41')], [10, 9, hex('#a93a41')],
  [7, 4, hex('#d9b365')], [8, 10, hex('#d9b365')], [5, 11, hex('#8a3038')], [10, 5, hex('#8a3038')],
]);
