/* Preferencia visual local: no escribe en el servidor ni modifica los formularios. */
(() => {
  'use strict';
  const key = 'sistema-juridico.presentacion';
  const root = document.documentElement;
  const cssUrl = document.currentScript.dataset.estandarCss;
  let stylesheet;
  let mode = 'estandar';
  try {
    if (localStorage.getItem(key) === 'rendimiento') mode = 'rendimiento';
  } catch (_) { /* Almacenamiento bloqueado: el selector sigue funcionando. */ }

  function apply(next) {
    mode = next;
    root.dataset.presentacion = mode;
    if (mode === 'estandar' && !stylesheet) {
      stylesheet = document.createElement('link');
      stylesheet.rel = 'stylesheet';
      stylesheet.href = cssUrl;
      stylesheet.id = 'estandar-css';
      document.head.appendChild(stylesheet);
    }
    if (stylesheet) stylesheet.disabled = mode !== 'estandar';
    const button = document.getElementById('modo-presentacion');
    if (button) button.setAttribute('aria-checked', String(mode === 'estandar'));
  }
  apply(mode);
  document.addEventListener('DOMContentLoaded', () => {
    const button = document.getElementById('modo-presentacion');
    if (!button) return;
    button.closest('.presentation-control').hidden = false;
    apply(mode);
    button.addEventListener('click', () => {
      apply(mode === 'estandar' ? 'rendimiento' : 'estandar');
      try { localStorage.setItem(key, mode); } catch (_) { /* Preferencia solo para esta página. */ }
    });
  });
  window.addEventListener('storage', event => {
    if (event.key === key || event.key === null) {
      apply(event.newValue === 'rendimiento' ? 'rendimiento' : 'estandar');
    }
  });
})();
