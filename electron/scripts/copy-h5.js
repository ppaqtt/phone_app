const fs = require('fs');
const path = require('path');

const sourceDir = path.join(__dirname, '../../uniapp/dist/build/h5');
const targetDir = path.join(__dirname, '../src/renderer/h5');

function copyDir(src, dest) {
  if (!fs.existsSync(dest)) {
    fs.mkdirSync(dest, { recursive: true });
  }
  
  const entries = fs.readdirSync(src, { withFileTypes: true });
  
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    
    if (entry.isDirectory()) {
      copyDir(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

if (fs.existsSync(targetDir)) {
  fs.rmSync(targetDir, { recursive: true, force: true });
}

if (!fs.existsSync(sourceDir)) {
  console.error('Error: H5 build not found at', sourceDir);
  console.error('Please run "cd ../uniapp && npm run build:h5" first.');
  process.exit(1);
}

copyDir(sourceDir, targetDir);
console.log('H5 build copied successfully!');