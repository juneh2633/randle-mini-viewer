(() => {
  'use strict';
  if (document.getElementById('randle-viewer-only-host')) return;
  const host = document.createElement('div');
  host.id = 'randle-viewer-only-host';
  document.documentElement.append(host);
  const ui = host.attachShadow({mode: 'open'});
  const theme = `*{box-sizing:border-box}section{color:#edf3ff;background:#142034;border:1px solid #536581;border-radius:14px;padding:14px;font:13px/1.5 system-ui,sans-serif}header{display:flex;justify-content:space-between;gap:10px;align-items:center}h2{font-size:15px;margin:0}.phase{font-size:11px;color:#a6d8ff}.digits{display:grid;grid-template-columns:repeat(8,minmax(0,1fr));gap:3px;margin:12px 0}.digits span{background:#243950;color:#edf3ff;text-align:center;border-radius:5px;padding:5px 0;font:700 25px/1.3 monospace}.status{font-size:12px;color:#bdcce0;margin:8px 0}.timer{font-size:12px;color:#ffe09d;min-height:18px;margin:6px 0}button{cursor:pointer;border:0;border-radius:8px;padding:9px 12px;color:#102030;background:#a6d8ff;font:700 12px system-ui}.actions{display:flex;gap:8px}.actions button{flex:1}[hidden]{display:none!important}.notice{font-size:11px;color:#bdcce0;margin:8px 0 0}section[data-attention="true"]{border-color:#ffe09d}section[data-attention="true"] .phase{color:#ffe09d}`;
  const markup = `<section aria-label="랜들 미니 뷰어"><header><h2>랜들 미니 뷰어</h2><span class="phase">대기</span></header><div class="digits" role="group" aria-label="현재 공개된 숫자">${Array(8).fill('<span>?</span>').join('')}</div><p class="status" role="status" aria-live="polite"></p><p class="timer"></p><p class="notice">보기 전용 · 게임은 랜들 탭에서 직접 진행하세요.</p></section>`;
  ui.innerHTML = `<style>:host{all:initial;position:fixed;right:16px;bottom:16px;z-index:2147483647;width:min(300px,calc(100vw - 24px))}${theme}#error{font:12px/1.5 system-ui;color:#ffe09d;background:#142034;padding:6px;border-radius:6px}.actions{margin-top:8px}</style>${markup}<div class="actions"><button id="open">항상 위 미니 창 ↗</button><button id="collapse" aria-expanded="true">접기</button></div><p id="error" role="status" hidden></p>`;
  let popup = null, opening = false, disposed = false, queued = false, lastSignature = '';
  function render(target, data) {
    target.querySelector('.phase').textContent = data.phase;
    target.querySelector('.status').textContent = data.status;
    target.querySelector('.timer').textContent = data.timer ? `사이트: ${data.timer}` : '';
    target.querySelector('section').dataset.attention = String(data.phase === '인증 필요');
    [...target.querySelectorAll('.digits span')].forEach((span, index) => {
      span.textContent = data.digits[index];
      span.setAttribute('aria-label', `${index + 1}번째 자리 ${data.digits[index]}`);
    });
  }
  function update(force = false) {
    if (disposed || !document?.defaultView) return;
    const data = RandleViewer.snapshot(document), signature = JSON.stringify(data);
    if (!force && signature === lastSignature) return;
    lastSignature = signature;
    render(ui, data);
    if (popup && !popup.closed) render(popup.document, data);
  }
  ui.getElementById('open').addEventListener('click', async () => {
    if (popup && !popup.closed) { popup.focus(); return; }
    if (opening) return;
    ui.getElementById('error').hidden = true;
    if (!window.documentPictureInPicture?.requestWindow) {
      ui.getElementById('error').textContent = '최신 데스크톱 Chrome 또는 Edge에서 사용해 주세요. 이 브라우저는 항상 위 미니 창을 지원하지 않습니다.';
      ui.getElementById('error').hidden = false; return;
    }
    opening = true;
    try {
      const opened = await window.documentPictureInPicture.requestWindow({width: 320, height: 260});
      if (disposed) { opened.close(); return; }
      popup = opened;
      const doc = opened.document;
      doc.title = '랜들 미니 뷰어'; doc.documentElement.lang = 'ko';
      const style = doc.createElement('style');
      style.textContent = `body{margin:0;background:#142034}${theme}section{border:0;border-radius:0;padding:16px}`;
      doc.head.append(style); doc.body.innerHTML = markup;
      opened.addEventListener('pagehide', () => { if (popup === opened) popup = null; }, {once: true});
      update(true);
    } catch {
      ui.getElementById('error').textContent = '미니 창을 열지 못했습니다. 랜들 탭에서 버튼을 다시 눌러 주세요.';
      ui.getElementById('error').hidden = false;
    } finally { opening = false; }
  });
  ui.getElementById('collapse').addEventListener('click', () => {
    const collapsed = !ui.querySelector('section').hidden;
    ui.querySelector('section').hidden = collapsed;
    ui.getElementById('collapse').textContent = collapsed ? '펼치기' : '접기';
    ui.getElementById('collapse').setAttribute('aria-expanded', String(!collapsed));
  });
  const observer = new MutationObserver(() => {
    if (queued || disposed) return;
    queued = true;
    queueMicrotask(() => { queued = false; update(); });
  });
  observer.observe(document.body, {subtree: true, childList: true, characterData: true, attributes: true});
  const poll = setInterval(update, 1000);
  window.addEventListener('pagehide', () => {
    disposed = true; observer.disconnect(); clearInterval(poll); popup?.close();
  }, {once: true});
  update();
})();
