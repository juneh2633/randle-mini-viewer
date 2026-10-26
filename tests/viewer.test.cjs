const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const {snapshot, perform} = require('../extension/viewer.js');
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
    assert.doesNotMatch(source, /dispatchEvent\s*\(|\bfetch\s*\(|\bWebSocket\b|\beval\s*\(|chrome\.|Runner|autoConfirm/);
  }
});
test('viewer PiP updates passively without game clicks and can reopen', async () => {
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
    assert.equal(popup.window.document.querySelectorAll('[data-action]').length, 12);
    assert.equal(popup.window.document.querySelectorAll('input, form').length, 0);
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
test('manual controls forward selection, select-all, reroll and confirmation once per request', () => {
  const dom = new JSDOM(fixture(), {url: 'https://randle.lixx.kr/play'});
  const doc = dom.window.document, game = doc.querySelector('section');
  game.insertAdjacentHTML('beforeend', '<button id="reroll" disabled>선택한 자리 다시 굴리기</button><button id="all">전체 선택</button>');
  let rerolls = 0, confirms = 0;
  const cells = [...doc.querySelectorAll('[data-roll-cell]')];
  cells[2].onclick = () => {cells[2].setAttribute('aria-pressed', 'true'); doc.getElementById('reroll').disabled = false;};
  doc.getElementById('all').onclick = () => cells.forEach(cell => cell.setAttribute('aria-pressed', 'true'));
  doc.getElementById('reroll').onclick = () => rerolls++;
  doc.getElementById('confirm').onclick = () => confirms++;
  assert.equal(perform(doc, 'reroll'), false);
  assert.equal(perform(doc, 'select', 2), true);
  assert.equal(snapshot(doc).selected[2], true);
  assert.equal(snapshot(doc).canReroll, true);
  assert.equal(perform(doc, 'selectAll'), true);
  assert.equal(snapshot(doc).selected.every(Boolean), true);
  assert.equal(perform(doc, 'reroll'), true);
  assert.equal(perform(doc, 'confirm'), true);
  assert.equal(rerolls, 1); assert.equal(confirms, 1);
  assert.equal(perform(doc, 'select', 8), false);
  assert.equal(perform(doc, 'unknown'), false);
  dom.window.close();
});
test('disabled, hidden, modal, inert and authentication states block manual controls', () => {
  const dom = new JSDOM(fixture(), {url: 'https://randle.lixx.kr/play'});
  const doc = dom.window.document, confirm = doc.getElementById('confirm');
  let clicks = 0; confirm.onclick = () => clicks++;
  confirm.disabled = true; assert.equal(perform(doc, 'confirm'), false);
  confirm.disabled = false; confirm.hidden = true; assert.equal(perform(doc, 'confirm'), false);
  confirm.hidden = false; doc.querySelector('section').setAttribute('inert', ''); assert.equal(perform(doc, 'confirm'), false);
  doc.querySelector('section').removeAttribute('inert');
  doc.body.insertAdjacentHTML('beforeend', '<dialog open>안내</dialog>');
  assert.equal(perform(doc, 'confirm'), false);
  doc.querySelector('dialog').remove();
  doc.body.insertAdjacentHTML('beforeend', '<button>인증하고 계속하기</button>');
  assert.equal(perform(doc, 'confirm'), false);
  assert.equal(snapshot(doc).canConfirm, false);
  assert.equal(clicks, 0);
  dom.window.close();
});
test('next-round control uses the site label and does not start a second action automatically', async () => {
  const dom = new JSDOM('<section aria-label="랜들 숫자 뽑기"><button>한 판 더 굴리기</button></section>', {url: 'https://randle.lixx.kr/play'});
  const doc = dom.window.document;
  let clicks = 0;
  doc.querySelector('button').onclick = event => {clicks++; event.target.textContent = '숫자 뽑기';};
  assert.equal(snapshot(doc).startLabel, '한 판 더 굴리기');
  assert.equal(perform(doc, 'start'), true);
  await new Promise(resolve => setTimeout(resolve, 20));
  assert.equal(clicks, 1);
  assert.equal(snapshot(doc).startLabel, '숫자 뽑기');
  dom.window.close();
});
test('PiP buttons operate the source game and keep selected positions synchronized', async () => {
  const dom = new JSDOM(fixture(), {url: 'https://randle.lixx.kr/play', runScripts: 'outside-only'});
  const popup = new JSDOM(''), {window} = dom;
  const doc = window.document;
  doc.querySelector('section').insertAdjacentHTML('beforeend', '<button id="reroll" disabled>선택한 자리 다시 굴리기</button><button id="all">전체 선택</button>');
  let rerolls = 0, confirms = 0;
  const cells = [...doc.querySelectorAll('[data-roll-cell]')];
  cells[2].onclick = () => {cells[2].setAttribute('aria-pressed', 'true'); doc.getElementById('reroll').disabled = false;};
  doc.getElementById('all').onclick = () => cells.forEach(cell => cell.setAttribute('aria-pressed', 'true'));
  doc.getElementById('reroll').onclick = () => rerolls++;
  doc.getElementById('confirm').onclick = () => confirms++;
  window.documentPictureInPicture = {requestWindow: async () => popup.window};
  for (const file of ['viewer.js', 'content.js']) window.eval(fs.readFileSync('extension/' + file, 'utf8'));
  try {
    doc.getElementById('randle-viewer-only-host').shadowRoot.getElementById('open').click();
    await new Promise(resolve => setTimeout(resolve, 10));
    const pip = popup.window.document;
    pip.querySelector('[data-index="2"]').click();
    assert.equal(pip.querySelector('[data-index="2"]').getAttribute('aria-pressed'), 'true');
    pip.querySelector('[data-action="selectAll"]').click();
    assert.equal([...pip.querySelectorAll('.digits button')].every(button => button.getAttribute('aria-pressed') === 'true'), true);
    pip.querySelector('[data-action="reroll"]').click();
    pip.querySelector('[data-action="confirm"]').click();
    assert.equal(rerolls, 1); assert.equal(confirms, 1);
    await new Promise(resolve => setTimeout(resolve, 20));
    assert.equal(confirms, 1, 'rendering must never repeat a game action');
  } finally { window.dispatchEvent(new window.Event('pagehide')); window.close(); }
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
