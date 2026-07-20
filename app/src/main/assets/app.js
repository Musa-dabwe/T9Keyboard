/* PoetBoard settings UI — shared palette, navigation and Android bridge helpers.
   Pages are plain HTML in assets/, hosted by WebViewActivity; the native side
   is exposed as the `Android` JavaScript interface. */

'use strict';

// Page-side mirror of KeyboardThemes (Kotlin) — keep both in sync.
const THEMES = {
  peach: { name: 'Soft Peach', swatch: 'linear-gradient(135deg,#f2a878,#e8895a)', vars: {
    '--bg-top': '#fff7f0', '--bg-bottom': '#fdeadd', '--ink': '#3d2f28', '--sub': '#a68a7b', '--lab': '#b09383',
    '--accent': '#f0a678', '--accent2': '#e37f4e', '--accsolid': '#e0763f', '--accsh': 'rgba(227,127,78,.5)',
    '--field': '#fffaf5', '--soft': '#fbe4d3', '--border': '#f0d8c6', '--popsh': 'rgba(120,70,40,.32)' } },
  rose: { name: 'Blush Rose', swatch: 'linear-gradient(135deg,#f7aac2,#ee7fa1)', vars: {
    '--bg-top': '#fff3f6', '--bg-bottom': '#ffe6ee', '--ink': '#4a2e39', '--sub': '#bd8b9c', '--lab': '#c295a5',
    '--accent': '#f5a3be', '--accent2': '#ec7699', '--accsolid': '#e06489', '--accsh': 'rgba(224,100,137,.5)',
    '--field': '#fff8fa', '--soft': '#ffe0ea', '--border': '#fad3df', '--popsh': 'rgba(150,60,90,.32)' } },
  lavender: { name: 'Lavender Mist', swatch: 'linear-gradient(135deg,#bcaef0,#9d8ce0)', vars: {
    '--bg-top': '#f5f2fc', '--bg-bottom': '#ece6fa', '--ink': '#3a3352', '--sub': '#948bb5', '--lab': '#948bb5',
    '--accent': '#b3a4e6', '--accent2': '#9179d6', '--accsolid': '#7b6ec4', '--accsh': 'rgba(145,121,214,.5)',
    '--field': '#faf8ff', '--soft': '#ece6fa', '--border': '#e3dcf6', '--popsh': 'rgba(80,60,130,.32)' } },
  sky: { name: 'Sky Blue', swatch: 'linear-gradient(135deg,#93c3f0,#5fa0e0)', vars: {
    '--bg-top': '#eef5fd', '--bg-bottom': '#e0eefb', '--ink': '#2b3d52', '--sub': '#8aa0b8', '--lab': '#8aa0b8',
    '--accent': '#8fc0ef', '--accent2': '#5fa0e0', '--accsolid': '#4a8fd6', '--accsh': 'rgba(95,160,224,.5)',
    '--field': '#f7fbff', '--soft': '#dcecfa', '--border': '#d3e6f7', '--popsh': 'rgba(40,90,150,.3)' } },
  mint: { name: 'Mint & Sky', swatch: 'linear-gradient(135deg,#7fd6ab,#6fbfd6)', vars: {
    '--bg-top': '#eefaf3', '--bg-bottom': '#e2f4f2', '--ink': '#2c4a3f', '--sub': '#83a89a', '--lab': '#83a89a',
    '--accent': '#6fc79e', '--accent2': '#5bb0c9', '--accsolid': '#3f9e78', '--accsh': 'rgba(91,176,201,.5)',
    '--field': '#f6fefb', '--soft': '#dcf3e8', '--border': '#cfeada', '--popsh': 'rgba(40,110,90,.3)' } },
  butter: { name: 'Butter Cream', swatch: 'linear-gradient(135deg,#f7db8a,#ecc45c)', vars: {
    '--bg-top': '#fffcf0', '--bg-bottom': '#fdf3d8', '--ink': '#4d4228', '--sub': '#b6a679', '--lab': '#b6a679',
    '--accent': '#f4d47a', '--accent2': '#e6b94e', '--accsolid': '#cf9f2f', '--accsh': 'rgba(230,185,78,.5)',
    '--field': '#fffdf6', '--soft': '#fdf0cf', '--border': '#f5e6bf', '--popsh': 'rgba(140,110,30,.3)' } },
};

const DEFAULT_THEME = 'sky';

// True inside the app; false when a page is opened in a desktop browser.
const hasBridge = typeof Android !== 'undefined';

function currentThemeId() {
  const id = hasBridge ? Android.getThemeId() : DEFAULT_THEME;
  return THEMES[id] ? id : DEFAULT_THEME;
}

function applyTheme(id) {
  const theme = THEMES[id] || THEMES[DEFAULT_THEME];
  const root = document.documentElement;
  for (const [k, v] of Object.entries(theme.vars)) root.style.setProperty(k, v);
}

// ---------- Shared chrome ----------

const NAV_PAGES = [
  ['setup', 'Setup'],
  ['settings', 'Settings'],
  ['privacy', 'Privacy'],
  ['logs', 'Logs'],
  ['about', 'About'],
];

function renderHeader(active, subtitle) {
  const head = document.createElement('div');
  head.innerHTML =
    '<div class="app-head">' +
    '  <div class="app-logo">PB</div>' +
    '  <div>' +
    '    <div class="app-title">PoetBoard</div>' +
    '    <div class="app-sub">' + subtitle + '</div>' +
    '  </div>' +
    '</div>';
  const nav = document.createElement('div');
  nav.className = 'nav';
  for (const [id, label] of NAV_PAGES) {
    const chip = document.createElement('div');
    chip.className = 'chip' + (id === active ? ' active' : '');
    chip.textContent = label;
    if (id !== active) chip.addEventListener('click', () => { location.href = id + '.html'; });
    nav.appendChild(chip);
  }
  const mount = document.getElementById('chrome');
  mount.appendChild(head);
  mount.appendChild(nav);
}

// ---------- Widget builders ----------

function makeToggle(initial, onChange) {
  const tog = document.createElement('div');
  tog.className = 'tog' + (initial ? ' on' : '');
  tog.innerHTML = '<span></span>';
  tog.addEventListener('click', () => {
    const on = !tog.classList.contains('on');
    tog.classList.toggle('on', on);
    onChange(on, tog);
  });
  return tog;
}

function toggleRow(title, sub, initial, onChange) {
  const row = document.createElement('div');
  row.className = 'row';
  row.innerHTML =
    '<div class="grow"><div class="row-title">' + title + '</div>' +
    (sub ? '<div class="row-sub">' + sub + '</div>' : '') + '</div>';
  row.appendChild(makeToggle(initial, onChange));
  return row;
}

function sliderRow(title, sub, min, max, initial, unit, onChange) {
  const row = document.createElement('div');
  row.className = 'row';
  row.innerHTML =
    '<div class="grow"><div class="row-title">' + title + '</div>' +
    (sub ? '<div class="row-sub">' + sub + '</div>' : '') + '</div>';
  const wrap = document.createElement('div');
  wrap.className = 'slider-wrap';
  const range = document.createElement('input');
  range.type = 'range';
  range.min = min;
  range.max = max;
  range.value = initial;
  const pill = document.createElement('span');
  pill.className = 'pill';
  const paint = () => { pill.textContent = range.value + (unit || ''); };
  paint();
  range.addEventListener('input', paint);
  range.addEventListener('change', () => onChange(parseInt(range.value, 10)));
  wrap.appendChild(range);
  wrap.appendChild(pill);
  row.appendChild(wrap);
  return row;
}

function saveSetting(key, value) {
  if (hasBridge) Android.saveSetting(key, String(value));
}
