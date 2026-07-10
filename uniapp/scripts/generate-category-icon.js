const fs = require('fs')
const path = require('path')

const crc32 = (data) => {
  let crc = 0xFFFFFFFF
  const table = []
  for (let i = 0; i < 256; i++) {
    let c = i
    for (let j = 0; j < 8; j++) {
      c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1)
    }
    table[i] = c
  }
  for (let i = 0; i < data.length; i++) {
    crc = table[(crc ^ data[i]) & 0xFF] ^ (crc >>> 8)
  }
  return (crc ^ 0xFFFFFFFF) >>> 0
}

const chunk = (type, data) => {
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length)
  const typeBuffer = Buffer.from(type)
  const crcData = Buffer.concat([typeBuffer, data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(crcData))
  return Buffer.concat([length, typeBuffer, data, crc])
}

const createPNG = (width, height, pixels) => {
  const signature = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
  
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  ihdr[10] = 0
  ihdr[11] = 0
  ihdr[12] = 0
  
  const rawData = []
  for (let y = 0; y < height; y++) {
    rawData.push(0)
    for (let x = 0; x < width; x++) {
      const idx = y * width + x
      const p = pixels[idx] || [0, 0, 0, 0]
      rawData.push(p[0], p[1], p[2], p[3])
    }
  }
  
  const zlib = require('zlib')
  const compressed = zlib.deflateSync(Buffer.from(rawData))
  
  return Buffer.concat([
    signature,
    chunk('IHDR', ihdr),
    chunk('IDAT', compressed),
    chunk('IEND', Buffer.alloc(0))
  ])
}

const drawPixel = (pixels, width, x, y, color) => {
  if (x >= 0 && x < width && y >= 0 && y < Math.floor(pixels.length / width)) {
    pixels[y * width + x] = color
  }
}

const drawLine = (pixels, width, x1, y1, x2, y2, color) => {
  const dx = Math.abs(x2 - x1)
  const dy = Math.abs(y2 - y1)
  const sx = x1 < x2 ? 1 : -1
  const sy = y1 < y2 ? 1 : -1
  let err = dx - dy
  let x = x1
  let y = y1
  
  while (true) {
    drawPixel(pixels, width, x, y, color)
    if (x === x2 && y === y2) break
    const e2 = 2 * err
    if (e2 > -dy) { err -= dy; x += sx }
    if (e2 < dx) { err += dx; y += sy }
  }
}

const drawRect = (pixels, width, x, y, w, h, color) => {
  for (let py = y; py < y + h; py++) {
    for (let px = x; px < x + w; px++) {
      drawPixel(pixels, width, px, py, color)
    }
  }
}

const drawCircle = (pixels, width, cx, cy, r, color, filled = true) => {
  for (let y = Math.max(0, cy - r); y <= Math.min(Math.floor(pixels.length / width) - 1, cy + r); y++) {
    for (let x = Math.max(0, cx - r); x <= Math.min(width - 1, cx + r); x++) {
      const dist = Math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
      if ((filled && dist <= r) || (!filled && Math.abs(dist - r) < 1)) {
        pixels[y * width + x] = color
      }
    }
  }
}

const createFolderIcon = (size, color) => {
  const pixels = new Array(size * size).fill(null)
  const pad = Math.floor(size * 0.15)
  const folderColor = color
  
  const folderY = pad + Math.floor(size * 0.18)
  const folderH = size - pad * 2 - Math.floor(size * 0.1)
  const folderW = size - pad * 2
  
  const r = Math.floor(size * 0.08)
  
  for (let y = folderY; y < folderY + folderH; y++) {
    for (let x = pad; x < pad + folderW; x++) {
      const dx = Math.min(x - pad, pad + folderW - 1 - x)
      const dy = Math.min(y - folderY, folderY + folderH - 1 - y)
      
      if (dx >= 0 && dy >= 0) {
        const cornerDist = Math.sqrt(Math.max(0, r - dx) ** 2 + Math.max(0, r - dy) ** 2)
        const isCorner = (dx < r && dy < r) ? cornerDist < r : true
        
        if (isCorner) {
          if (dx < 2 || dy < 2) {
            pixels[y * size + x] = folderColor
          } else {
            pixels[y * size + x] = [
              Math.min(255, folderColor[0] + 15),
              Math.min(255, folderColor[1] + 15),
              Math.min(255, folderColor[2] + 15),
              folderColor[3]
            ]
          }
        }
      }
    }
  }
  
  const tabY = pad
  const tabH = Math.floor(size * 0.18)
  const tabW = Math.floor(size * 0.38)
  
  for (let y = tabY; y < tabY + tabH; y++) {
    for (let x = pad; x < pad + tabW; x++) {
      const dx = x - pad
      const dy = y - tabY
      const cornerDist = Math.sqrt(Math.max(0, r - dx) ** 2 + Math.max(0, r - dy) ** 2)
      const isCorner = (dx < r && dy < r) ? cornerDist < r : true
      
      if (isCorner) {
        pixels[y * size + x] = folderColor
      }
    }
  }
  
  return createPNG(size, size, pixels)
}

const iconsDir = path.join(__dirname, '../src/static/icons')

const normalColor = [156, 156, 156, 255]
const activeColor = [92, 107, 192, 255]

const normal = createFolderIcon(96, normalColor)
const active = createFolderIcon(96, activeColor)

fs.writeFileSync(path.join(iconsDir, 'category.png'), normal)
fs.writeFileSync(path.join(iconsDir, 'category-active.png'), active)

console.log(`Created category.png (${normal.length} bytes)`)
console.log(`Created category-active.png (${active.length} bytes)`)
console.log('Done!')
