const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
function setup() {
  const dom = new JSDOM('<section aria-label="랜들 숫자 뽑기"><button>숫자 뽑기</button></section>', {url: 'https://randle.lixx.kr/play', runScripts: 'outside-only'});
  const {window} = dom;
  let now = 0, starts = 0, confirms = 0;
  window.Date.now = () => now;
  const game = window.document.querySelector('section');
  const draw = () => {
    starts++;
    game.innerHTML = '<div aria-label="8자리 숫자">' + [...'00123456'].map((digit, index) => `<button data-roll-cell aria-label="${index + 1}번째 자리, 숫자 ${digit}">${digit}</button>`).join('') + '</div><button id="confirm">이 숫자로 확정 (15초 후 자동 확정)</button>';
    game.querySelector('#confirm').onclick = () => {
      confirms++; game.innerHTML = '<button>한 판 더 굴리기</button>';
      game.querySelector('button').onclick = draw;
    };
  };
  game.querySelector('button').onclick = draw;
  for (const file of ['extension/viewer.js', 'android/app/src/main/assets/auto.js']) window.eval(fs.readFileSync(file, 'utf8'));
  return {dom, window, auto: window.RandleAndroidAuto, advance: ms => {now += ms;}, counts: () => [starts, confirms]};
}
test('Android auto stays off until the exact command and does not evaluate input', () => {
  const {dom, window, auto, advance, counts} = setup();
  assert.equal(auto.command('/auto start;window.hacked=true'), false);
  assert.equal(window.hacked, undefined);
  assert.equal(auto.command('/auto resume'), false);
  for (let i = 0; i < 10; i++) {advance(1000); auto.snapshot();}
  assert.deepEqual(counts(), [0, 0]); assert.equal(auto.status().enabled, false);
  dom.window.close();
});
test('Android auto repeatedly draws and confirms with a two-second review window', () => {
  const {dom, auto, advance, counts} = setup();
  assert.equal(auto.command('/auto start'), true);
  auto.snapshot(); assert.deepEqual(counts(), [1, 0]);
  auto.snapshot(); advance(1999); auto.snapshot(); assert.deepEqual(counts(), [1, 0]);
  advance(1); auto.snapshot(); assert.deepEqual(counts(), [1, 1]);
  advance(1499); auto.snapshot(); assert.deepEqual(counts(), [1, 1]);
  advance(1); auto.snapshot(); assert.deepEqual(counts(), [2, 1]);
  auto.snapshot(); advance(2000); auto.snapshot(); assert.deepEqual(counts(), [2, 2]);
  dom.window.close();
});
test('pause cancels pending confirmation, resume restarts the review delay and stop relocks', () => {
  const {dom, auto, advance, counts} = setup();
  auto.command('/auto start'); auto.snapshot(); auto.snapshot(); advance(1500);
  auto.command('/auto pause'); advance(5000); auto.snapshot(); assert.deepEqual(counts(), [1, 0]);
  auto.command('/auto resume'); auto.snapshot(); advance(1999); auto.snapshot(); assert.deepEqual(counts(), [1, 0]);
  advance(1); auto.snapshot(); assert.deepEqual(counts(), [1, 1]);
  auto.command('/auto stop'); assert.equal(auto.status().enabled, false);
  assert.equal(auto.command('/auto resume'), false);
  dom.window.close();
});
test('authentication pauses Android automation without touching the challenge or auto-resuming', () => {
  const {dom, window, auto, advance, counts} = setup();
  auto.command('/auto start'); auto.snapshot(); auto.snapshot();
  window.document.body.insertAdjacentHTML('beforeend', '<button id="human">인증하고 계속하기</button>');
  let challengeClicks = 0; window.document.getElementById('human').onclick = () => challengeClicks++;
  advance(2500); auto.snapshot(); assert.equal(auto.status().running, false);
  assert.equal(auto.command('/auto resume'), false);
  window.document.getElementById('human').remove(); advance(5000); auto.snapshot();
  assert.deepEqual(counts(), [1, 0]); assert.equal(challengeClicks, 0);
  dom.window.close();
});
test('reinjecting the Android script preserves a session but a new document starts manual', () => {
  const {dom, window, auto} = setup(); auto.command('/auto start'); auto.snapshot();
  window.eval(fs.readFileSync('android/app/src/main/assets/auto.js', 'utf8'));
  assert.equal(window.RandleAndroidAuto, auto); assert.equal(auto.status().running, true);
  const fresh = setup(); assert.equal(fresh.auto.status().enabled, false);
  dom.window.close(); fresh.dom.window.close();
});
