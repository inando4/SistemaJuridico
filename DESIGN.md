---
name: Sistema Jurídico
description: Sistema visual local para la coordinación del trabajo jurídico
colors:
  tinta: "#18394c"
  texto: "#203442"
  tenue: "#526675"
  borde: "#cbd5dd"
  fondo: "#f2f5f7"
  fondo-suave: "#eaf0f4"
  papel: "#fff"
  enlace: "#155b7c"
  urgente: "#a52c3b"
  aviso: "#815700"
  ok: "#226443"
  foco: "#267ca5"
  nav-texto: "#e3edf3"
  nav-hover: "#274b60"
  nav-activo: "#e9f1f6"
  boton-hover: "#28536b"
  boton-activo: "#102b3b"
  campo-borde: "#9fadb8"
  tabla-cabecera: "#e7eef3"
typography:
  headline:
    fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif'
    fontSize: "1.8rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-.025em"
  title:
    fontSize: "1.2rem"
    fontWeight: 700
    lineHeight: 1.3
  body:
    fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif'
    fontSize: ".9375rem"
    lineHeight: 1.55
  label:
    fontSize: ".875rem"
    fontWeight: 600
  table:
    fontSize: ".8125rem"
  metric:
    fontSize: "2.6rem"
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "-.03em"
rounded:
  control: ".5rem"
  navigation: ".375rem"
  metrics: ".75rem"
spacing:
  small: ".5rem"
  medium: "1rem"
  panel: "1.5rem"
  section: "2rem"
components:
  button-primary:
    backgroundColor: "{colors.tinta}"
    textColor: "{colors.papel}"
    rounded: "{rounded.control}"
    padding: ".55rem 1rem"
  button-primary-hover:
    backgroundColor: "{colors.boton-hover}"
  button-secondary:
    backgroundColor: "{colors.papel}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.control}"
    padding: ".3rem .55rem"
  input:
    backgroundColor: "{colors.papel}"
    textColor: "{colors.texto}"
    rounded: "{rounded.control}"
    padding: ".65rem .75rem"
  form-panel:
    backgroundColor: "{colors.papel}"
    rounded: "{rounded.control}"
    padding: "1.5rem"
  nav-item:
    textColor: "{colors.nav-texto}"
    rounded: "{rounded.navigation}"
    padding: ".6rem .85rem"
---

# Design System: Sistema Jurídico

## Overview

**Creative North Star: "Tablero de coordinación del área"**

El sistema organiza trabajo jurídico cotidiano con azul tinta, superficies blancas y un fondo gris frío. La jerarquía se apoya en tipografía del sistema, divisores y estados legibles, con una densidad apropiada para formularios y registros.

Esta documentación captura la implementación Estándar construida, superpuesta a la base local existente. No incorpora fuentes externas, imágenes decorativas ni bibliotecas visuales adicionales. La presentación Rendimiento conserva el vocabulario original de app.css; sus valores no deben sustituirse por estos tokens.

**Key Characteristics:**

- Azul tinta como ancla institucional y de navegación.
- Superficies planas con bordes finos y radios contenidos.
- Estados con texto, foco visible y cifras tabulares.

## Colors

El azul tinta y los neutros fríos separan orientación, contenido y acciones sin competir con los estados del trabajo.

### Primary

- **Tinta institucional:** navegación, títulos, monograma y acciones principales.
- **Azul de enlace:** enlaces de contenido y marca del día actual en calendario.

### Neutral

- **Papel:** formularios, tablas y cabecera institucional.
- **Fondo y fondo suave:** suelo de la página y campos deshabilitados.
- **Texto, tenue y borde:** lectura principal, contexto y separación de registros.
- **Texto de navegación, selección y hover:** contraste sobre el panel tinta.

Los tonos urgente, aviso y ok expresan estados existentes. Siempre deben conservar sus rótulos; no constituyen categorías decorativas. Los valores normativos figuran en el frontmatter y proceden de estandar.css.

## Typography

Se usa la misma pila de fuentes del sistema para títulos, cuerpo y controles. Los títulos son compactos y de peso fuerte; las tablas reducen el tamaño para mantener información comparable. No existe una fuente de exhibición separada.

Los roles headline, title, body, label, table y metric reflejan la jerarquía implementada. El título principal baja a (1.5rem) y las cifras a (2rem) en móvil. Las cifras de tablas y métricas usan números tabulares. Los párrafos introductorios tienen un máximo de (72ch). La etiqueta de institución es secundaria y más pequeña; no compite con el nombre del sistema.

## Layout

La cabecera ocupa todo el ancho. En escritorio, una columna lateral de (14.5rem) contiene las secciones; el contenido tiene un máximo de (78rem) y un ancho equivalente al disponible menos (5rem). Bajo (900px), la navegación mide (11.75rem) y se reducen los márgenes. Hasta (680px), el documento pasa a bloque y la navegación se envuelve en filas encima del contenido, con margen lateral de (1rem).

Los formularios principales son paneles blancos. Los filtros expandidos usan cuatro columnas desde (1100px). Las tablas dentro de regiones conservan un ancho mínimo de (64rem); los calendarios, (44rem), con desplazamiento horizontal en sus contenedores. La impresión oculta cabeceras y navegación y aprovecha todo el ancho.

## Elevation & Depth

La implementación no utiliza sombras. La profundidad procede de superficies blancas sobre fondo gris frío, bordes finos y cambios de tono en hover. No se añaden elevaciones a los paneles.

## Shapes

Los controles y paneles comparten el radio control; los enlaces de navegación tienen un radio menor. Los paneles de métricas usan un contorno común de radio metrics y celdas internas rectangulares. Los campos se delimitan con un borde sólido de (1px). El selector de presentación usa una pista redondeada y un pulgar circular.

## Components

### Buttons

Las acciones principales son tinta sobre papel invertido, con peso (600), altura mínima (2.625rem) y estados de hover y pulsación. Las acciones de tabla son compactas y blancas, con borde visible. Cancelar y retirar conservan texto rojo y fondo blanco, con hover rojo pálido. Deshabilitado reduce la opacidad a (.55).

### Inputs / Fields

Los campos son blancos, de ancho completo, con borde campo-borde. El foco visible usa un contorno de (3px) y separación de (3px). Los inválidos usan el borde urgente y los deshabilitados el fondo suave. Las áreas de texto permiten ajuste vertical y tienen altura mínima de (7rem).

### Cards / Containers

Formularios, detalles y regiones de tabla comparten papel, borde y radio control. El relleno habitual del formulario es panel, reducido a medium en móvil. El panel de métricas agrupa enlaces en tres columnas, y en dos hasta (900px), con divisores internos. Sus números conservan las etiquetas y los estados existentes; esta composición es propia del panel del día.

### Navigation

La navegación principal usa fondo tinta, enlaces claros y página actual con fondo pálido y peso (700). Hover aclara el fondo. Su foco visible utiliza nav-texto para contrastar con el panel oscuro; no aplicar aquí el azul de foco de los campos. Los grupos son controles nativos details/summary. La disposición móvil conserva enlaces visibles y permite envolver filas.

### Tables and status

Las cabeceras tienen fondo tabla-cabecera; las filas usan separadores horizontales y un hover tenue. El relleno de celdas es (.8rem .85rem). Los avisos de estado son bloques textuales, con fondo amarillo pálido o rojo pálido en errores. No hay un sistema nuevo de chips.

### Presentation switch

El selector es un botón nativo con role switch, etiqueta accesible y aria-checked. Los textos Rendimiento y Estándar permanecen visibles, con peso fuerte en la opción activa. Su pista mide (3rem × 1.75rem). La única transición añadida es el desplazamiento del pulgar en Estándar: (160ms ease-out), únicamente cuando el usuario no solicita movimiento reducido. La estrategia de modos y del panel inicial se registra en el brief de superficie.

## Do's and Don'ts

### Do:

- Do mantener los recursos visuales locales y la tipografía del sistema.
- Do acompañar los estados de plazo con texto y conservar el foco visible.
- Do reutilizar los tokens de Estándar dentro de su hoja específica.
- Do conservar tablas desplazables y navegación accesible en pantallas estrechas.

### Don't:

- Don't introducir sombras, fuentes remotas o imágenes decorativas sin revisar las restricciones del producto.
- Don't modificar la base Rendimiento para aplicar el aspecto Estándar.
- Don't ocultar información de estado o acciones para acomodar el diseño móvil.
