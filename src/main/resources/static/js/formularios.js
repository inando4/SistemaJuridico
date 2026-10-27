/* Mejora la orientación; los mensajes y enlaces funcionan también sin JavaScript. */
document.addEventListener('DOMContentLoaded', function () {
  var resumen = document.querySelector('.resumen-errores:not([hidden])');
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

/*
 * Confirmación antes de una acción que no se deshace: los formularios con
 * data-confirmar abren #dialogo-confirmar en vez de enviarse. Sin JavaScript, o
 * en un navegador sin <dialog>, el formulario se envía directamente.
 */
document.addEventListener('DOMContentLoaded', function () {
  var dialogo = document.getElementById('dialogo-confirmar');
  if (!dialogo || typeof dialogo.showModal !== 'function') return;
  var cita = dialogo.querySelector('[data-confirmar-cita]');
  var formulario = null;
  var origen = null;

  document.addEventListener('submit', function (evento) {
    var destino = evento.target;
    if (!destino.matches('form[data-confirmar]')) return;
    evento.preventDefault();
    formulario = destino;
    origen = evento.submitter || document.activeElement;
    cita.textContent = destino.dataset.confirmar;
    cita.className = 'dialogo-confirmar-cita nota-' + (destino.dataset.confirmarColor || 'amarillo');
    dialogo.returnValue = '';
    dialogo.showModal();
  });

  // Pulsar fuera del cuadro equivale a Cancelar.
  dialogo.addEventListener('click', function (evento) {
    if (evento.target === dialogo) dialogo.close('cancelar');
  });

  dialogo.addEventListener('close', function () {
    var elegido = formulario;
    formulario = null;
    if (dialogo.returnValue === 'confirmar' && elegido) {
      // submit() no dispara el evento submit: no vuelve a preguntar.
      elegido.submit();
    } else if (origen && document.contains(origen)) {
      origen.focus();
    }
  });
});
