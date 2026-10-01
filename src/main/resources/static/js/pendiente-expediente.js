/* El formulario principal se conserva en el DOM mientras se registra un expediente. */
document.addEventListener('DOMContentLoaded', function () {
  'use strict';

  const vinculo = document.querySelector('[data-vinculo-expediente]');
  if (!vinculo) return;

  const pendiente = vinculo.closest('form');
  const tipos = vinculo.querySelector('[data-tipos-vinculo]');
  const paneles = Array.from(vinculo.querySelectorAll('[data-panel-vinculo]'));
  const confirmacion = vinculo.querySelector('[data-vinculo-confirmacion]');
  const advertencia = vinculo.querySelector('[data-vinculo-advertencia]');
  const avisoInicial = pendiente.querySelector('[data-aviso-expediente]');
  let ultimoCreado = null;

  function tipoActual() {
    return tipos.querySelector('input:checked')?.value || 'ninguno';
  }

  function actualizarVinculo() {
    const tipo = tipoActual();
    let elegido = '';
    paneles.forEach(function (panel) {
      const activo = panel.dataset.panelVinculo === tipo;
      const selector = panel.querySelector('select');
      panel.hidden = !activo;
      // Conservar la elección al cambiar de tipo, pero enviar SOLO el vínculo activo.
      selector.disabled = !activo;
      selector.required = activo;
      if (activo) elegido = selector.value;
    });
    vinculo.querySelector('[data-sin-vinculo]').hidden = tipo !== 'ninguno';
    if (avisoInicial) avisoInicial.hidden = avisoInicial.dataset.avisoExpediente !== elegido;
    confirmacion.hidden = !ultimoCreado || ultimoCreado.tipo !== tipo || ultimoCreado.id !== elegido;
    advertencia.hidden = confirmacion.hidden || !advertencia.textContent;
  }

  tipos.hidden = false;
  vinculo.addEventListener('change', actualizarVinculo);
  window.addEventListener('pageshow', actualizarVinculo);
  pendiente.addEventListener('reset', function () { setTimeout(actualizarVinculo, 0); });
  // Los enlaces del resumen de errores también pueden activar un vínculo oculto.
  document.addEventListener('click', function (event) {
    const enlace = event.target.closest('.resumen-errores a[href^="#"]');
    if (!enlace) return;
    const campo = document.getElementById(enlace.hash.slice(1));
    const panel = campo?.closest('[data-panel-vinculo]');
    if (!panel) return;
    tipos.querySelector('[value="' + panel.dataset.panelVinculo + '"]').checked = true;
    actualizarVinculo();
  }, true);
  actualizarVinculo();

  const modal = document.getElementById('modal-expediente');
  if (!modal || typeof modal.showModal !== 'function') return;

  const titulo = modal.querySelector('#titulo-modal-expediente');
  const cuerpo = modal.querySelector('.cuerpo-modal-expediente');
  const contenido = modal.querySelector('[data-contenido-expediente]');
  const carga = modal.querySelector('[data-carga-expediente]');
  const errores = modal.querySelector('[data-errores-expediente]');
  const mensajeError = errores.querySelector('[data-mensaje-error]');
  const listaErrores = errores.querySelector('[data-lista-errores]');
  const reintentar = errores.querySelector('[data-reintentar-expediente]');
  const acceso = errores.querySelector('[data-acceso-expediente]');
  const formularios = new Map();
  let origen = null;
  let tipoAbierto = null;
  let solicitudCarga = null;
  let guardando = false;
  let camposBloqueados = [];
  let focoAlCerrar = null;

  vinculo.querySelectorAll('[data-crear-expediente]').forEach(function (boton) {
    boton.hidden = false;
    boton.addEventListener('click', function () {
      origen = boton;
      tipoAbierto = boton.dataset.crearExpediente;
      focoAlCerrar = boton;
      titulo.textContent = tipoAbierto === 'judicial'
        ? 'Crear expediente judicial' : 'Crear procedimiento administrativo';
      limpiarErrores();
      contenido.replaceChildren();
      carga.hidden = true;
      modal.showModal();
      titulo.focus();
      if (formularios.has(tipoAbierto)) {
        montarFormulario(formularios.get(tipoAbierto));
      } else {
        cargarFormulario();
      }
    });
  });
  vinculo.querySelectorAll('[data-alta-alternativa]').forEach(function (enlace) { enlace.hidden = true; });

  function limpiarErrores() {
    errores.hidden = true;
    listaErrores.replaceChildren();
    reintentar.hidden = true;
    acceso.hidden = true;
    contenido.querySelectorAll('[data-error-modal]').forEach(function (p) { p.remove(); });
    contenido.querySelectorAll('[aria-invalid="true"]').forEach(function (campo) {
      campo.removeAttribute('aria-invalid');
      campo.removeAttribute('aria-describedby');
    });
  }

  function mostrarError(mensaje, campos, recuperarSesion) {
    mensajeError.textContent = mensaje;
    listaErrores.replaceChildren();
    errores.hidden = false;
    acceso.hidden = !recuperarSesion;
    const formulario = contenido.querySelector('form');
    Object.entries(campos || {}).forEach(function ([nombre, texto]) {
      const campo = formulario?.elements.namedItem(nombre);
      const fila = document.createElement('li');
      if (campo && campo.id) {
        const errorCampo = document.createElement('p');
        errorCampo.id = campo.id + '-error-modal';
        errorCampo.className = 'error';
        errorCampo.dataset.errorModal = '';
        errorCampo.textContent = texto;
        campo.setAttribute('aria-invalid', 'true');
        campo.setAttribute('aria-describedby', errorCampo.id);
        campo.insertAdjacentElement('afterend', errorCampo);
        const enlace = document.createElement('a');
        enlace.href = '#' + campo.id;
        const etiqueta = campo.labels?.[0]?.textContent.trim().replace(/\s*\*$/, '') || nombre;
        enlace.textContent = etiqueta + ': ' + texto;
        enlace.addEventListener('click', function (event) {
          event.preventDefault();
          campo.focus();
          campo.scrollIntoView({ block: 'center' });
        });
        fila.append(enlace);
      } else {
        fila.textContent = texto;
      }
      listaErrores.append(fila);
    });
    errores.focus();
    cuerpo.scrollTop = 0;
  }

  function sesionNoDisponible(respuesta) {
    return respuesta.redirected || respuesta.status === 401 || respuesta.status === 403;
  }

  function montarFormulario(formulario) {
    contenido.replaceChildren(formulario);
    cuerpo.scrollTop = 0;
    (formulario.querySelector('[name="caseNumber"], [name="fileNumber"]') || titulo).focus();
  }

  function actualizarTokenDeSesion(formularioNuevo) {
    // Solo un formulario recién descargado puede renovar el token: un borrador
    // de otro tipo puede conservar el token de una sesión que ya terminó.
    const token = formularioNuevo.querySelector('input[name="_csrf"]');
    if (!token) return;
    [pendiente, ...formularios.values()].forEach(function (formulario) {
      const campo = formulario.querySelector('input[name="_csrf"]');
      if (campo) campo.value = token.value;
    });
  }

  function conservarBorrador(anterior, nuevo) {
    if (!anterior) return;
    Array.from(anterior.elements).forEach(function (campo) {
      if (!campo.name || campo.type === 'hidden' || campo.tagName === 'BUTTON') return;
      const destino = nuevo.elements.namedItem(campo.name);
      if (!destino) return;
      if (destino.tagName === 'SELECT' && campo.value &&
          !Array.from(destino.options).some(function (opcion) { return opcion.value === campo.value; })) {
        // Mantener una selección retirada para que el servidor la señale, sin reasignar en silencio.
        destino.add(new Option(campo.selectedOptions[0].textContent + ' (ya no disponible)', campo.value));
      }
      destino.value = campo.value;
    });
  }

  async function cargarFormulario() {
    if (solicitudCarga) solicitudCarga.abort();
    const solicitud = new AbortController();
    const limiteEspera = setTimeout(function () { solicitud.abort(); }, 30000);
    solicitudCarga = solicitud;
    const tipo = tipoAbierto;
    const anterior = formularios.get(tipo);
    limpiarErrores();
    carga.textContent = 'Cargando formulario…';
    carga.hidden = false;
    contenido.setAttribute('aria-busy', 'true');
    try {
      const respuesta = await fetch(origen.dataset.formularioUrl, {
        headers: { Accept: 'text/html' }, credentials: 'same-origin',
        cache: 'no-store', signal: solicitud.signal
      });
      if (solicitud.signal.aborted || !modal.open || solicitudCarga !== solicitud) return;
      if (sesionNoDisponible(respuesta)) {
        mostrarError('La sesión venció o ya no tiene acceso. Inicie sesión en otra pestaña y vuelva a cargar el formulario. Sus datos se conservarán.', null, true);
        reintentar.hidden = false;
        return;
      }
      if (!respuesta.ok) throw new Error('No se pudo cargar el formulario');
      const documento = new DOMParser().parseFromString(await respuesta.text(), 'text/html');
      const formulario = documento.querySelector('form[data-expediente-tipo="' + tipo + '"]');
      if (!formulario) throw new Error('Formulario inesperado');
      if (solicitud.signal.aborted || !modal.open || solicitudCarga !== solicitud) return;
      conservarBorrador(anterior, formulario);
      actualizarTokenDeSesion(formulario);
      formulario.addEventListener('submit', guardarExpediente);
      formularios.set(tipo, formulario);
      montarFormulario(formulario);
    } catch (error) {
      if (!modal.open || solicitudCarga !== solicitud) return;
      mostrarError('No se pudo cargar el formulario. Revise su conexión y vuelva a intentarlo. Lo escrito en el pendiente se conserva.');
      reintentar.hidden = false;
    } finally {
      clearTimeout(limiteEspera);
      if (solicitudCarga === solicitud) {
        solicitudCarga = null;
        carga.hidden = true;
        contenido.removeAttribute('aria-busy');
      }
    }
  }

  reintentar.addEventListener('click', cargarFormulario);

  function estadoGuardado(activo, formulario) {
    guardando = activo;
    if (activo) {
      // FormData ya está capturado. Impedir cambios que no entrarían en ese POST.
      camposBloqueados = Array.from(formulario.querySelectorAll('input, select, textarea'))
        .filter(function (campo) { return !campo.disabled; });
      camposBloqueados.forEach(function (campo) { campo.disabled = true; });
    } else {
      camposBloqueados.forEach(function (campo) { campo.disabled = false; });
      camposBloqueados = [];
    }
    modal.querySelectorAll('button').forEach(function (boton) { boton.disabled = activo; });
    formulario.setAttribute('aria-busy', String(activo));
    formulario.querySelector('button[type="submit"]').textContent = activo ? 'Creando…' : 'Crear y seleccionar';
    carga.textContent = 'Creando expediente…';
    carga.hidden = !activo;
  }

  async function guardarExpediente(event) {
    event.preventDefault();
    if (guardando || solicitudCarga) return;
    const formulario = event.currentTarget;
    const datos = new URLSearchParams(new FormData(formulario));
    const solicitud = new AbortController();
    const limiteEspera = setTimeout(function () { solicitud.abort(); }, 45000);
    limpiarErrores();
    estadoGuardado(true, formulario);
    try {
      const respuesta = await fetch(formulario.action, {
        method: 'POST', body: datos, credentials: 'same-origin',
        headers: { Accept: 'application/json' }, cache: 'no-store', signal: solicitud.signal
      });
      if (sesionNoDisponible(respuesta)) {
        mostrarError('La sesión venció o ya no tiene acceso. Inicie sesión en otra pestaña y vuelva a cargar el formulario. Lo escrito se conserva.', null, true);
        reintentar.hidden = false;
        return;
      }
      if (!(respuesta.headers.get('content-type') || '').includes('application/json')) {
        throw new Error('Respuesta inesperada');
      }
      const resultado = await respuesta.json();
      if (respuesta.status === 422 && resultado.errores) {
        mostrarError('Revise los campos indicados. Lo que escribió se conserva.', resultado.errores);
        return;
      }
      if (!respuesta.ok || !resultado.id || !resultado.numero) throw new Error('Alta no confirmada');

      const panel = paneles.find(function (p) { return p.dataset.panelVinculo === tipoAbierto; });
      const selector = panel.querySelector('select');
      const opcion = new Option(resultado.numero, resultado.id, false, true);
      selector.add(opcion);
      selector.value = resultado.id;
      tipos.querySelector('[value="' + tipoAbierto + '"]').checked = true;
      ultimoCreado = { id: resultado.id, tipo: tipoAbierto };
      const prefijo = tipoAbierto === 'judicial' ? 'Expediente ' : 'Procedimiento ';
      confirmacion.textContent = prefijo + resultado.numero + ' creado y seleccionado. Guarde el pendiente para vincularlo.';
      advertencia.textContent = resultado.advertencia || '';
      if (formulario.elements.namedItem('ownerId')) {
        confirmacion.append(' El responsable del pendiente será usted, aunque haya asignado el expediente a otra persona.');
      }
      panel.querySelector('[data-lista-vacia]')?.remove();
      actualizarVinculo();
      formularios.delete(tipoAbierto);
      focoAlCerrar = selector;
      modal.close();
    } catch (error) {
      // No reintentar un POST automáticamente: la escritura pudo llegar al servidor.
      mostrarError('No se pudo confirmar la creación. Sus datos se conservan. Revise su conexión y compruebe si el expediente se creó antes de volver a guardar.');
    } finally {
      clearTimeout(limiteEspera);
      estadoGuardado(false, formulario);
    }
  }

  modal.addEventListener('click', function (event) {
    if (event.target.closest('[data-cerrar-expediente]') && !guardando) modal.close();
  });
  modal.addEventListener('cancel', function (event) {
    if (guardando) event.preventDefault();
  });
  modal.addEventListener('close', function () {
    if (solicitudCarga) solicitudCarga.abort();
    focoAlCerrar?.focus();
  });
});
