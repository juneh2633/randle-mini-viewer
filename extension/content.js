(() => {
  'use strict';
  if (document.getElementById('randle-viewer-only-host')) return;
  const host = document.createElement('div');
  host.id = 'randle-viewer-only-host';
  document.documentElement.append(host);
  const ui = host.attachShadow({mode: 'open'});
  const theme = `*{box-sizing:border-box}section{color:#edf3ff;background:#142034;border:1px solid #536581;border-radius:14px;padding:14px;font:13px/1.5 system-ui,sans-serif}header{display:flex;justify-content:space-between;gap:10px;align-items:center}h2{font-size:15px;margin:0}.phase{font-size:11px;color:#a6d8ff}.digits{display:grid;grid-template-columns:repeat(8,minmax(0,1fr));gap:3px;margin:12px 0}.digits span{background:#243950;color:#edf3ff;text-align:center;border-radius:5px;padding:5px 0;font:700 25px/1.3 monospace}.status{font-size:12px;color:#bdcce0;margin:8px 0}.timer{font-size:12px;color:#ffe09d;min-height:18px;margin:6px 0}button{cursor:pointer;border:0;border-radius:8px;padding:9px 12px;color:#102030;background:#a6d8ff;font:700 12px system-ui}.actions{display:flex;gap:8px}.actions button{flex:1}[hidden]{display:none!important}.notice{font-size:11px;color:#bdcce0;margin:8px 0 0}section[data-attention="true"]{border-color:#ffe09d}section[data-attention="true"] .phase{color:#ffe09d}`;
  const controlTheme = '.digits button{min-width:0;padding:5px 0;font:700 25px/1.3 monospace;background:#243950;color:#edf3ff}.digits button[aria-pressed="true"]{background:#ffe09d;color:#142034}button:disabled{cursor:default;opacity:.45}.controls{display:grid;grid-template-columns:1fr 1fr;gap:8px}.controls button{min-height:36px}.controls [data-action="start"]{grid-column:1/-1}.feedback{color:#ffe09d;font-size:12px;margin:6px 0}.feedback:empty{display:none}';
  const markup = `<section aria-label="랜들 미니 뷰어"><header><h2>랜들 미니 뷰어</h2><span class="phase">대기</span></header><div class="digits" role="group" aria-label="현재 공개된 숫자와 리롤할 자리">${Array.from({length:8}, (_, index) => `<button data-action="select" data-index="${index}" aria-pressed="false" disabled>?</button>`).join('')}</div><p class="status" role="status" aria-live="polite"></p><p class="timer"></p><div class="controls"><button data-action="reroll" disabled>선택한 자리 굴리기</button><button data-action="selectAll" disabled>전체 선택</button><button data-action="confirm" disabled>이 숫자로 확정</button><button data-action="start" disabled>숫자 뽑기</button></div><p class="feedback" role="status"></p><p class="notice">버튼을 누를 때만 조작합니다. 사이트 제한 시간은 계속 흐릅니다.</p></section>`;
  ui.innerHTML = `<style>:host{all:initial;position:fixed;right:16px;bottom:16px;z-index:2147483647;width:min(300px,calc(100vw - 24px))}${theme}#error{font:12px/1.5 system-ui;color:#ffe09d;background:#142034;padding:6px;border-radius:6px}.actions{margin-top:8px}</style>${markup}<div class="actions"><button id="open">항상 위 미니 창 ↗</button><button id="collapse" aria-expanded="true">접기</button></div><p id="error" role="status" hidden></p>`;
  let popup = null, opening = false, disposed = false, queued = false, lastSignature = '';
  function bindControls(target) {
    target.querySelectorAll('[data-action]').forEach(button => {
      button.addEventListener('click', () => {
        if (disposed) return;
        const accepted = RandleViewer.perform(document, button.dataset.action, button.dataset.index === undefined ? undefined : Number(button.dataset.index));
        update(true);
        target.querySelector('.feedback').textContent = accepted ? '' : '상태가 바뀌어 조작하지 못했습니다. 잠시 후 다시 눌러 주세요.';
      });
    });
  }
  const mainControlStyle = document.createElement('style');
  mainControlStyle.textContent = controlTheme; ui.append(mainControlStyle);
  bindControls(ui);
  function render(target, data) {
    target.querySelector('.phase').textContent = data.phase;
    target.querySelector('.status').textContent = data.status;
    target.querySelector('.timer').textContent = data.timer ? `사이트: ${data.timer}` : '';
    target.querySelector('section').dataset.attention = String(data.phase === '인증 필요');
    [...target.querySelectorAll('.digits button')].forEach((button, index) => {
      button.textContent = data.digits[index];
      button.disabled = !data.selectable[index];
      button.setAttribute('aria-label', `${index + 1}번째 자리 ${data.digits[index]} 리롤 선택`);
      button.setAttribute('aria-pressed', String(data.selected[index]));
    });
    for (const [action, property] of [['reroll','canReroll'], ['selectAll','canSelectAll'], ['confirm','canConfirm'], ['start','canStart']]) target.querySelector(`[data-action="${action}"]`).disabled = !data[property];
    target.querySelector('[data-action="start"]').textContent = data.startLabel;
    target.querySelector('.feedback').textContent = '';
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
      const opened = await window.documentPictureInPicture.requestWindow({width: 340, height: 400});
      if (disposed) { opened.close(); return; }
      popup = opened;
      const doc = opened.document;
      doc.title = '랜들 미니 뷰어'; doc.documentElement.lang = 'ko';
      const style = doc.createElement('style');
      style.textContent = `body{margin:0;background:#142034}${theme}${controlTheme}section{border:0;border-radius:0;padding:16px}`;
      doc.head.append(style); doc.body.innerHTML = markup;
      bindControls(doc);
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
