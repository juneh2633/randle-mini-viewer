(function (root) {
  'use strict';
  function visible(element) {
    if (!element?.isConnected) return false;
    for (let node = element; node?.nodeType === 1; node = node.parentElement) {
      if (node.hidden || node.getAttribute('aria-hidden') === 'true' || (node.tagName === 'DIALOG' && !node.open)) return false;
      const style = node.ownerDocument.defaultView.getComputedStyle(node);
      if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
    }
    return true;
  }
  function snapshot(document) {
    const empty = {digits: Array(8).fill('?'), phase: '대기', status: '랜들 뽑기 페이지를 열어 주세요.', timer: ''};
    if (!document?.defaultView || document.location.pathname.replace(/\/$/, '') !== '/play') return empty;
    const game = document.querySelector('section[aria-label="랜들 숫자 뽑기"]');
    if (!game) return {...empty, status: '페이지가 준비되거나 로그인될 때까지 기다립니다.'};
    const cells = [...game.querySelectorAll('[aria-label="8자리 숫자"] [data-roll-cell]')];
    const digits = Array.from({length: 8}, (_, index) => {
      const match = cells[index]?.getAttribute('aria-label')?.match(/^(\d+)번째 자리, 숫자 (\d)$/);
      return match && Number(match[1]) === index + 1 ? match[2] : '?';
    });
    const human = [...document.querySelectorAll('[aria-label="인간 인증"], button, [role="status"]')].some(el =>
      visible(el) && (el.getAttribute('aria-label') === '인간 인증' || /인증하고 계속하기|인간 인증을 마치면|아래 인증을 마치면/.test(el.textContent)));
    if (human) return {digits, phase: '인증 필요', status: '원래 랜들 탭에서 직접 인증해 주세요.', timer: ''};
    const confirm = [...game.querySelectorAll('button')].find(el => visible(el) && /^이 숫자로 확정/.test(el.textContent.trim()));
    if (confirm) return {digits, phase: '숫자 확인', status: '리롤과 확정은 원래 랜들 탭에서 직접 해 주세요.', timer: confirm.textContent.match(/\(([^)]*자동 확정)\)/)?.[1] ?? ''};
    const confirmed = !!game.querySelector('[aria-label="8자리 숫자"].is-confirmed') || [...game.querySelectorAll('p')].some(el => /이번 판을 확정했어요/.test(el.textContent));
    if (confirmed) return {digits, phase: '확정 완료', status: '이번 판에 확정된 숫자입니다.', timer: ''};
    return {digits, phase: digits.every(d => d === '?') ? '대기' : '숫자 공개 중', status: '사이트에서 공개된 자리부터 표시합니다.', timer: ''};
  }
  root.RandleViewer = {snapshot};
  if (typeof module !== 'undefined') module.exports = root.RandleViewer;
})(globalThis);
