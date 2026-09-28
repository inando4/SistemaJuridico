/* Preferencia visual local: no escribe en el servidor ni modifica los formularios. */
(() => {
  'use strict';
  const key = 'sistema-juridico.presentacion';
  const root = document.documentElement;
  const cssUrl = document.currentScript.dataset.estandarCss;
  let stylesheet;
  // El arranque inline ya resolvio la preferencia y anticipo su descarga.
  let mode = root.dataset.presentacion === 'rendimiento' ? 'rendimiento' : 'estandar';

  function apply(next) {
    mode = next;
    root.dataset.presentacion = mode;
    if (mode === 'estandar' && !stylesheet) {
      stylesheet = document.createElement('link');
      stylesheet.rel = 'stylesheet';
      stylesheet.href = cssUrl;
      stylesheet.id = 'estandar-css';
      // Una hoja añadida por JavaScript no bloquea el primer pintado por defecto.
      stylesheet.setAttribute('blocking', 'render');
      if (!document.body) {
        // Respaldo para navegadores sin blocking="render". Solo durante el arranque.
        root.setAttribute('data-presentacion-cargando', '');
        const revealContent = () => root.removeAttribute('data-presentacion-cargando');
        stylesheet.addEventListener('load', revealContent, { once: true });
        stylesheet.addEventListener('error', revealContent, { once: true });
      }
      document.head.appendChild(stylesheet);
    }
    if (stylesheet) stylesheet.disabled = mode !== 'estandar';
    if (mode !== 'estandar') root.removeAttribute('data-presentacion-cargando');
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
