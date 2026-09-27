/* Mejora la orientación; los mensajes y enlaces funcionan también sin JavaScript. */
document.addEventListener('DOMContentLoaded', function () {
  var resumen = document.querySelector('.resumen-errores');
  if (!resumen) return;
  resumen.querySelectorAll('a[href^="#"]').forEach(function (enlace) {
    var campo = document.getElementById(enlace.hash.slice(1));
    if (!campo) return;
    if (campo.labels && campo.labels.length) {
      enlace.textContent = campo.labels[0].textContent.trim() + ': ' + enlace.textContent;
    }
    enlace.addEventListener('click', function (evento) {
      evento.preventDefault();
      campo.focus();
      campo.scrollIntoView({ block: 'center' });
    });
  });
  resumen.focus();
});
