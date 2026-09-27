async (page) => {
  const routes = ['/', '/alertas', '/pendientes', '/pendientes/hoy', '/cumplidos', '/buscar?q=AUD', '/equipo', '/calendario', '/actividad-diaria', '/judiciales', '/administrativos', '/configuracion', '/dias-no-laborables', '/usuarios', '/usuarios/nuevo', '/tipos-de-pendiente', '/prioridades', '/estados-de-pendiente', '/estados-procesales', '/estados-administrativos', '/pendientes/nuevo', '/judiciales/nuevo', '/administrativos/nuevo', '/cuenta/contrasena', '/judiciales/4108e336-557a-4a5c-a332-5a12f451c7df', '/administrativos/c9144ed2-5416-43e6-b8a3-9c0c6ae347d3', '/pendientes/09323cc0-b6bf-4973-be32-2f58dccff205', '/pendientes/09323cc0-b6bf-4973-be32-2f58dccff205/historial'];
  const screenshots = new Set(['/', '/pendientes', '/pendientes/hoy', '/cumplidos', '/calendario', '/equipo', '/judiciales/4108e336-557a-4a5c-a332-5a12f451c7df', '/administrativos/c9144ed2-5416-43e6-b8a3-9c0c6ae347d3', '/pendientes/09323cc0-b6bf-4973-be32-2f58dccff205/historial']);
  page._auditErrors = [];
  page.on('pageerror',e=>page._auditErrors.push(e.message));
  const results=[];
  const mode=await page.evaluate(()=>localStorage.getItem('sistema-juridico.presentacion') || 'estandar');
  for (const width of [1440,390]) {
    await page.setViewportSize({width,height:900});
    for (let i=0;i<routes.length;i++) {
      const route=routes[i];
      const response=await page.goto('http://localhost:18090'+route,{waitUntil:'load'});
      const state=await page.evaluate(()=>{
        const visible=e=>!!(e.getClientRects().length) && getComputedStyle(e).visibility!=='hidden';
        const controls=[...document.querySelectorAll('input:not([type=hidden]),select,textarea')].filter(visible);
        return {title:document.title,width:innerWidth,docWidth:document.documentElement.scrollWidth,mainWidth:document.querySelector('main')?.getBoundingClientRect().width,unlabelled:controls.filter(e=>!e.labels?.length&&!e.getAttribute('aria-label')&&!e.getAttribute('aria-labelledby')).map(e=>e.id||e.name),smallTargets:[...document.querySelectorAll('button,summary')].filter(visible).filter(e=>e.getBoundingClientRect().height<24).map(e=>e.textContent.trim().slice(0,70)),mainTop:document.querySelector('main')?.getBoundingClientRect().top,style:document.documentElement.dataset.presentacion,missingTranslation:/\?\?[^?]+\?\?/.test(document.body.innerText),rows:document.querySelectorAll('tbody tr').length,ms:Math.round(performance.getEntriesByType('navigation')[0].duration)};
      });
      results.push({route,status:response.status(),...state});
      if(screenshots.has(route)) await page.screenshot({path:'target/auditoria-2026-09-26/'+mode+'-'+width+'-'+i+'.png',fullPage:false});
    }
  }
  return {mode,results,jsErrors:page._auditErrors};
}
