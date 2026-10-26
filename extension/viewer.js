(function (root) {
  'use strict';
  function visible(element) {
    if (!element?.isConnected) return false;
    for (let node = element; node?.nodeType === 1; node = node.parentElement) {
      if (node.hidden || node.inert || node.hasAttribute('inert') || node.getAttribute('aria-hidden') === 'true' || (node.tagName === 'DIALOG' && !node.open)) return false;
      const style = node.ownerDocument.defaultView.getComputedStyle(node);
      if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
    }
    return true;
  }
  const text = element => (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
  function controls(document) {
    if (!document?.defaultView || document.location.pathname.replace(/\/$/, '') !== '/play') return {};
    const game = document.querySelector('section[aria-label="랜들 숫자 뽑기"]');
    if (!game) return {};
    const human = [...document.querySelectorAll('[aria-label="인간 인증"], button, [role="status"]')].some(el =>
      visible(el) && (el.getAttribute('aria-label') === '인간 인증' || /인증하고 계속하기|인간 인증을 마치면|아래 인증을 마치면/.test(text(el))));
    const blocked = human || !visible(game) || [...document.querySelectorAll('dialog[open], [role="dialog"], [aria-modal="true"]')].some(visible);
    const buttons = [...game.querySelectorAll('button')].filter(visible);
    const find = pattern => buttons.find(button => pattern.test(text(button)));
    return {
      game, human, blocked,
      cells: [...game.querySelectorAll('[aria-label="8자리 숫자"] [data-roll-cell]')],
      confirm: find(/^이 숫자로 확정(?:\s|\(|$)/),
      reroll: find(/^선택한 자리 다시 굴리기$/),
      selectAll: find(/^전체 선택$/),
      start: find(/^(숫자 뽑기|다음 판 시작|한 판 더 굴리기)$/)
    };
  }
  function enabled(element, state) {
    return !state.blocked && !!element && visible(element) && !element.disabled && element.getAttribute('aria-disabled') !== 'true';
  }
  function snapshot(document) {
    const empty = {digits: Array(8).fill('?'), selected: Array(8).fill(false), selectable: Array(8).fill(false), canConfirm: false, canReroll: false, canSelectAll: false, canStart: false, startLabel: '숫자 뽑기', phase: '대기', status: '랜들 뽑기 페이지를 열어 주세요.', timer: ''};
    const state = controls(document), {game} = state;
    if (!document?.defaultView || document.location.pathname.replace(/\/$/, '') !== '/play') return empty;
    if (!game) return {...empty, status: '페이지가 준비되거나 로그인될 때까지 기다립니다.'};
    const cells = state.cells;
    const digits = Array.from({length: 8}, (_, index) => {
      const match = cells[index]?.getAttribute('aria-label')?.match(/^(\d+)번째 자리, 숫자 (\d)$/);
      return match && Number(match[1]) === index + 1 ? match[2] : '?';
    });
    const base = {
      ...empty, digits,
      selected: Array.from({length: 8}, (_, index) => cells[index]?.getAttribute('aria-pressed') === 'true'),
      selectable: Array.from({length: 8}, (_, index) => enabled(state.confirm, state) && enabled(cells[index], state)),
      canConfirm: enabled(state.confirm, state),
      canReroll: enabled(state.reroll, state),
      canSelectAll: enabled(state.selectAll, state),
      canStart: enabled(state.start, state),
      startLabel: text(state.start) || '숫자 뽑기'
    };
    if (state.human) return {...base, phase: '인증 필요', status: '원래 랜들 탭에서 직접 인증해 주세요.'};
    if (state.blocked) return {...base, phase: '조작 대기', status: '원래 랜들 탭의 안내를 확인해 주세요.'};
    if (state.confirm) return {...base, phase: '숫자 확인', status: '바꿀 자리를 누른 뒤 리롤하거나 이 숫자로 확정하세요.', timer: text(state.confirm).match(/\(([^)]*자동 확정)\)/)?.[1] ?? ''};
    const confirmed = !!game.querySelector('[aria-label="8자리 숫자"].is-confirmed') || [...game.querySelectorAll('p')].some(el => /이번 판을 확정했어요/.test(el.textContent));
    if (confirmed) return {...base, phase: '확정 완료', status: '이번 판에 확정된 숫자입니다.'};
    return {...base, phase: state.start ? '대기' : digits.every(d => d === '?') ? '대기' : '숫자 공개 중', status: state.start ? '버튼을 눌러 다음 숫자를 뽑으세요.' : '사이트에서 공개된 자리부터 표시합니다.'};
  }
  // Only called by a viewer button's click handler. One request forwards one click.
  function perform(document, action, index) {
    const state = controls(document);
    if (!state.game || state.blocked) return false;
    let target;
    if (action === 'select' && Number.isInteger(index) && index >= 0 && index < 8 && enabled(state.confirm, state)) target = state.cells[index];
    else if (['confirm', 'reroll', 'selectAll', 'start'].includes(action)) target = state[action];
    if (!enabled(target, state)) return false;
    target.click();
    return true;
  }
  root.RandleViewer = {snapshot, perform};
  if (typeof module !== 'undefined') module.exports = root.RandleViewer;
})(globalThis);
