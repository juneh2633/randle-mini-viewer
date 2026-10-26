const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const {snapshot} = require('../extension/viewer.js');
function fixture(number = '00123456') {
  return `<section aria-label="랜들 숫자 뽑기"><div aria-label="8자리 숫자">${[...number].map((digit, index) => `<button data-roll-cell ${index < 2 ? 'hidden' : ''} aria-label="${index + 1}번째 자리, 숫자 ${digit}">${digit}</button>`).join('')}</div><button id="confirm">이 숫자로 확정 (12초 후 자동 확정)</button></section>`;
}
test('viewer reads unconfirmed digits with leading zeroes, countdown and partial reveal', () => {
  const dom = new JSDOM(fixture(), {url: 'https://randle.lixx.kr/play'});
  assert.equal(snapshot(dom.window.document).digits.join(''), '00123456');
  assert.equal(snapshot(dom.window.document).timer, '12초 후 자동 확정');
  dom.window.document.querySelector('[data-roll-cell]').setAttribute('aria-label', '1번째 자리, 굴리는 중');
  assert.equal(snapshot(dom.window.document).digits.join(''), '?0123456');
  dom.window.close();
});
test('viewer recognizes authentication without taking any action', () => {
  const dom = new JSDOM(fixture() + '<dialog aria-label="인간 인증">문제</dialog>', {url: 'https://randle.lixx.kr/play'});
  assert.equal(snapshot(dom.window.document).phase, '숫자 확인');
  dom.window.document.querySelector('dialog').open = true;
  assert.equal(snapshot(dom.window.document).phase, '인증 필요');
  dom.window.close();
});
test('viewer handles confirmed and unavailable game screens', () => {
  const dom = new JSDOM(fixture(), {url: 'https://randle.lixx.kr/play'});
  dom.window.document.getElementById('confirm').remove();
  dom.window.document.querySelector('[aria-label="8자리 숫자"]').classList.add('is-confirmed');
  assert.equal(snapshot(dom.window.document).phase, '확정 완료');
  dom.window.history.pushState({}, '', '/me');
  assert.equal(snapshot(dom.window.document).phase, '대기');
  dom.window.close();
});
test('release package requests no optional permissions and includes no automation modules', () => {
  const manifest = JSON.parse(fs.readFileSync('extension/manifest.json', 'utf8'));
  assert.equal(manifest.permissions, undefined);
  assert.equal(manifest.host_permissions, undefined);
  assert.equal(manifest.background, undefined);
  assert.deepEqual(manifest.content_scripts[0].js, ['viewer.js', 'content.js']);
  assert.equal(manifest.name, '랜들 미니 뷰어');
  const files = fs.readdirSync('extension').sort();
  assert.deepEqual(files, ['README.md', 'content.js', 'icon.png', 'manifest.json', 'viewer.js']);
  for (const file of manifest.content_scripts[0].js) {
    const source = fs.readFileSync('extension/' + file, 'utf8');
    assert.doesNotMatch(source, /\.click\s*\(|dispatchEvent\s*\(|\bfetch\s*\(|\bWebSocket\b|\beval\s*\(|chrome\.|Runner|autoConfirm/);
  }
});
test('viewer PiP updates instantly without ever clicking a game button, and can reopen', async () => {
  const dom = new JSDOM(fixture(), {url: 'https://randle.lixx.kr/play', runScripts: 'outside-only'});
  const popup = new JSDOM('');
  const {window} = dom;
  let clicks = 0, opens = 0;
  window.document.querySelector('section').addEventListener('click', () => clicks++);
  window.documentPictureInPicture = {requestWindow: async () => {opens++; return popup.window;}};
  for (const file of ['viewer.js', 'content.js']) window.eval(fs.readFileSync('extension/' + file, 'utf8'));
  try {
    const ui = window.document.getElementById('randle-viewer-only-host').shadowRoot;
    ui.getElementById('open').click();
    await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(popup.window.document.querySelector('.digits').textContent, '00123456');
    assert.equal(popup.window.document.querySelectorAll('button, input, form').length, 0);
    window.document.querySelectorAll('[data-roll-cell]')[3].setAttribute('aria-label', '4번째 자리, 숫자 9');
    await new Promise(resolve => setTimeout(resolve, 20));
    assert.equal(popup.window.document.querySelector('.digits').textContent, '00193456');
    ui.getElementById('collapse').click();
    assert.equal(ui.querySelector('section').hidden, true);
    popup.window.dispatchEvent(new popup.window.Event('pagehide'));
    ui.getElementById('open').click();
    await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(opens, 2);
    assert.equal(clicks, 0);
  } finally {
    window.dispatchEvent(new window.Event('pagehide')); window.close();
  }
});
test('unsupported browser leaves the viewer usable and shows a clear message', () => {
  const dom = new JSDOM(fixture(), {url: 'https://randle.lixx.kr/play', runScripts: 'outside-only'});
  for (const file of ['viewer.js', 'content.js']) dom.window.eval(fs.readFileSync('extension/' + file, 'utf8'));
  const ui = dom.window.document.getElementById('randle-viewer-only-host').shadowRoot;
  ui.getElementById('open').click();
  assert.equal(ui.getElementById('error').hidden, false);
  assert.match(ui.getElementById('error').textContent, /Chrome 또는 Edge/);
  dom.window.dispatchEvent(new dom.window.Event('pagehide')); dom.window.close();
});
