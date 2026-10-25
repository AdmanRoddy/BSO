// The same YouTube check in today's Chrome on the same machine, to tell "old engine" apart from
// "YouTube doesn't trust this computer".
const puppeteer = require('puppeteer-core');
const fs = require('fs');
(async () => {
  const b = await puppeteer.launch({ executablePath: process.env.CHROME || '/usr/bin/google-chrome', headless: 'new',
    args: ['--autoplay-policy=no-user-gesture-required', '--no-sandbox'] });
  const YT = 'https://admanroddy.github.io/BSO/tvtest/yt.html';
  for (const [name, url] of [['video', YT + '#jNQXAC9IVRw'], ['channel', YT + '?list=UULFvlG_ZnHoZLPv0CVWOC0a1Q']]) {
    const p = await b.newPage(); await p.setViewport({ width: 1920, height: 1080 });
    const lines = [];
    p.on('console', (m) => { if (/^YT /.test(m.text())) lines.push(m.text()); });
    await p.goto(url); await new Promise((r) => setTimeout(r, 40000));
    fs.mkdirSync('out/modern-' + name, { recursive: true });
    await p.screenshot({ path: 'out/modern-' + name + '/yt-40s.png' });
    fs.writeFileSync('out/modern-' + name + '/log.txt', lines.join('\n') + '\n');
    console.log(name + ':\n' + lines.slice(-6).join('\n'));
    await p.close();
  }
  // J R-dio: how many stations the directory hands this machine
  const p = await b.newPage(); const lines = [];
  p.on('console', (m) => { if (/J R-dio/.test(m.text())) lines.push(m.text()); });
  await p.goto('https://admanroddy.github.io/BSO/radio/'); await new Promise((r) => setTimeout(r, 60000));
  const probe = await p.evaluate(() => window.bsoProbe && window.bsoProbe());
  fs.mkdirSync('out/modern-radio', { recursive: true });
  fs.writeFileSync('out/modern-radio/log.txt', lines.join('\n') + '\nPROBE ' + JSON.stringify(probe) + '\n');
  console.log('radio:', lines.slice(-4).join(' | '), JSON.stringify(probe));
  await b.close();
})();
