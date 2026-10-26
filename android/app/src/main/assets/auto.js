(function (root) {
  'use strict';
  if (root.RandleAndroidAuto) return;
  let enabled = false, running = false, message = '수동 모드';
  let lastClick = -Infinity, reviewAt = null, reviewNumber = '', progressAt = 0, progressKey = '';
  function pause(reason = '자동 일시 정지') { running = false; reviewAt = null; if (enabled) message = reason; }
  function status() { return {enabled, running, message}; }
  function here() { return location.origin === 'https://randle.lixx.kr' && /^\/play\/?$/.test(location.pathname); }
  function command(value) {
    if (value === '/auto stop') { pause(); enabled = false; message = '수동 모드'; return true; }
    if (value === '/auto pause') { pause(); return true; }
    if (!['/auto start', '/auto resume'].includes(value) || !here() || (value === '/auto resume' && !enabled)) return false;
    const current = root.RandleViewer.snapshot(document);
    if (current.phase === '인증 필요') { pause('인증 필요 · 직접 해결한 뒤 재개하세요.'); return false; }
    enabled = true; running = true; message = '자동 뽑기 실행 중';
    lastClick = -Infinity; reviewAt = null; progressAt = Date.now(); progressKey = '';
    return true;
  }
  function tick(current) {
    if (!running) return;
    if (!here()) { pause('페이지 이동으로 자동 정지'); return; }
    if (current.phase === '인증 필요') { pause('인증 필요 · 직접 해결한 뒤 재개하세요.'); return; }
    const now = Date.now();
    const key = [current.phase, current.digits.join(''), current.canConfirm, current.canStart, current.startLabel].join(':');
    if (key !== progressKey) { progressKey = key; progressAt = now; }
    if (now - progressAt >= 180000) { pause('3분간 진행이 없어 자동 정지'); return; }
    let action;
    if (current.canConfirm && current.digits.length === 8 && current.digits.every(digit => /^\d$/.test(digit))) {
      const number = current.digits.join('');
      if (reviewAt === null || reviewNumber !== number) { reviewAt = now; reviewNumber = number; }
      const remaining = Math.max(0, 1000 - (now - reviewAt));
      if (remaining > 0) { message = `${(remaining / 1000).toFixed(1)}초 후 자동 확정 · 필요하면 일시 정지`; return; }
      action = 'confirm';
    } else {
      reviewAt = null;
      if (current.canStart) action = 'start';
    }
    if (!action || now - lastClick < 1000) { message = '자동 뽑기 · 사이트 응답 대기'; return; }
    // The manual-control module rechecks disabled controls, modals and CAPTCHA
    // immediately before the single click. No endpoints or timers are bypassed.
    if (root.RandleViewer.perform(document, action)) { lastClick = now; reviewAt = null; }
    message = '자동 뽑기 실행 중';
  }
  function snapshot() {
    const current = root.RandleViewer.snapshot(document);
    tick(current);
    return {...current, automation: status()};
  }
  const manual = event => {
    if (event.isTrusted && event.target instanceof Element && event.target.closest('section[aria-label="랜들 숫자 뽑기"] button')) pause('직접 조작하여 자동 일시 정지');
  };
  document.addEventListener('pointerdown', manual, true);
  document.addEventListener('click', manual, true);
  root.RandleAndroidAuto = {command, pause, status, snapshot};
})(globalThis);
