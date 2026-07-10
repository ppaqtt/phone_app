const fs = require('fs')
const path = require('path')

const iconsDir = path.join(__dirname, '../src/static/icons')

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

const drawRect = (pixels, width, x, y, w, h, color) => {
  for (let py = y; py < y + h; py++) {
    for (let px = x; px < x + w; px++) {
      if (px >= 0 && px < width && py >= 0 && py < Math.floor(pixels.length / width)) {
        pixels[py * width + px] = color
      }
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

const drawLine = (pixels, width, x1, y1, x2, y2, color) => {
  const dx = Math.abs(x2 - x1)
  const dy = Math.abs(y2 - y1)
  const sx = x1 < x2 ? 1 : -1
  const sy = y1 < y2 ? 1 : -1
  let err = dx - dy
  let x = x1
  let y = y1
  
  while (true) {
    if (x >= 0 && x < width && y >= 0 && y < Math.floor(pixels.length / width)) {
      pixels[y * width + x] = color
    }
    if (x === x2 && y === y2) break
    const e2 = 2 * err
    if (e2 > -dy) { err -= dy; x += sx }
    if (e2 < dx) { err += dx; y += sy }
  }
}

const drawRoundRect = (pixels, width, x, y, w, h, r, color, filled = true) => {
  if (filled) {
    drawRect(pixels, width, x + r, y, w - r * 2, h, color)
    drawRect(pixels, width, x, y + r, r, h - r * 2, color)
    drawRect(pixels, width, x + w - r, y + r, r, h - r * 2, color)
    
    drawCircle(pixels, width, x + r, y + r, r, color)
    drawCircle(pixels, width, x + w - r, y + r, r, color)
    drawCircle(pixels, width, x + r, y + h - r, r, color)
    drawCircle(pixels, width, x + w - r, y + h - r, r, color)
  } else {
    drawLine(pixels, width, x + r, y, x + w - r, y, color)
    drawLine(pixels, width, x + r, y + h, x + w - r, y + h, color)
    drawLine(pixels, width, x, y + r, x, y + h - r, color)
    drawLine(pixels, width, x + w, y + r, x + w, y + h - r, color)
    
    const drawArc = (cx, cy, r, startAngle, endAngle) => {
      const startX = cx + r * Math.cos(startAngle)
      const startY = cy + r * Math.sin(startAngle)
      const endX = cx + r * Math.cos(endAngle)
      const endY = cy + r * Math.sin(endAngle)
      
      for (let angle = startAngle; angle <= endAngle; angle += 0.1) {
        const px = cx + r * Math.cos(angle)
        const py = cy + r * Math.sin(angle)
        if (px >= 0 && px < width && py >= 0 && py < Math.floor(pixels.length / width)) {
          pixels[Math.floor(py) * width + Math.floor(px)] = color
        }
      }
    }
    
    drawArc(x + r, y + r, r, Math.PI, Math.PI * 1.5)
    drawArc(x + w - r, y + r, r, Math.PI * 1.5, Math.PI * 2)
    drawArc(x + r, y + h - r, r, Math.PI * 0.5, Math.PI)
    drawArc(x + w - r, y + h - r, r, 0, Math.PI * 0.5)
  }
}

const createNoteIcon = (size, color) => {
  const pixels = new Array(size * size).fill(null)
  const pad = 4
  
  drawRoundRect(pixels, size, pad, pad + 4, size - pad * 2, size - pad * 2 - 8, 4, color)
  
  const lineY = [pad + 14, pad + 22, pad + 30, pad + 38]
  lineY.forEach(y => {
    drawLine(pixels, size, pad + 6, y, size - pad - 6, y, color)
  })
  
  drawLine(pixels, size, pad + 10, pad + 4, pad + 10, pad + 18, color)
  
  return createPNG(size, size, pixels)
}

const createCategoryIcon = (size, color) => {
  const pixels = new Array(size * size).fill(null)
  const pad = 4
  
  drawRoundRect(pixels, size, pad, pad + 10, size - pad * 2, size - pad * 2 - 6, 6, color)
  
  drawRect(pixels, size, pad, pad, 16, 14, color)
  
  return createPNG(size, size, pixels)
}

const createSearchIcon = (size, color) => {
  const pixels = new Array(size * size).fill(null)
  const cx = size / 2
  const cy = size / 2
  const r = (size - 16) / 2
  
  for (let y = Math.max(0, cy - r); y <= Math.min(size - 1, cy + r); y++) {
    for (let x = Math.max(0, cx - r); x <= Math.min(size - 1, cx + r); x++) {
      const dist = Math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
      if (Math.abs(dist - r) < 1.5) {
        pixels[y * size + x] = color
      }
    }
  }
  
  const angle = Math.PI / 4
  const lx = cx + r * Math.cos(angle)
  const ly = cy + r * Math.sin(angle)
  const ex = lx + 8
  const ey = ly + 8
  
  drawLine(pixels, size, lx, ly, ex, ey, color)
  
  return createPNG(size, size, pixels)
}

const createSettingsIcon = (size, color) => {
  const pixels = new Array(size * size).fill(null)
  const cx = size / 2
  const cy = size / 2
  const r = (size - 16) / 2
  
  for (let i = 0; i < 8; i++) {
    const angle = (i * Math.PI) / 4
    const x = cx + r * Math.cos(angle)
    const y = cy + r * Math.sin(angle)
    
    const isHorizontal = i % 2 === 0
    const len = isHorizontal ? 8 : 5
    const half = len / 2
    
    if (isHorizontal) {
      drawLine(pixels, size, x - half, y, x + half, y, color)
    } else {
      drawLine(pixels, size, x, y - half, x, y + half, color)
    }
  }
  
  drawCircle(pixels, size, cx, cy, 6, color)
  
  return createPNG(size, size, pixels)
}

const icons = [
  { name: 'note', create: createNoteIcon },
  { name: 'category', create: createCategoryIcon },
  { name: 'search', create: createSearchIcon },
  { name: 'settings', create: createSettingsIcon },
]

const normalColor = [156, 156, 156, 255]
const activeColor = [92, 107, 192, 255]

icons.forEach(icon => {
  const normal = icon.create(48, normalColor)
  const active = icon.create(48, activeColor)
  
  fs.writeFileSync(path.join(iconsDir, `${icon.name}.png`), normal)
  fs.writeFileSync(path.join(iconsDir, `${icon.name}-active.png`), active)
  
  console.log(`Created ${icon.name}.png (${normal.length} bytes)`)
})

console.log('All icons generated!')