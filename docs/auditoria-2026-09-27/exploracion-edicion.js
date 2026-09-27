async (page) => {
  const out={};
  await page.goto('http://localhost:18090/tipos-de-pendiente');
  await page.getByRole('row').filter({has:page.getByRole('cell',{name:'Revisión',exact:true})}).getByRole('button',{name:'Deshabilitar',exact:true}).click();
  await page.goto('http://localhost:18090/pendientes/09323cc0-b6bf-4973-be32-2f58dccff205/editar');
  out.disabledCatalog={selected:await page.locator('#pendingTaskTypeId').inputValue(),options:await page.locator('#pendingTaskTypeId').innerText()};
  await page.locator('#notes').fill('Solo corregí las observaciones');
  await page.getByRole('button',{name:'Guardar',exact:false}).click();
  out.disabledCatalog.saved=await page.locator('tr').filter({has:page.getByRole('rowheader',{name:'Tipo',exact:true})}).innerText();
  await page.screenshot({path:'target/auditoria-2026-09-26/catalogo-perdido.png',fullPage:false});
  await page.goto('http://localhost:18090/tipos-de-pendiente');
  await page.getByRole('row').filter({has:page.getByRole('cell',{name:'Revisión',exact:true})}).getByRole('button',{name:'Habilitar',exact:true}).click();

  await page.goto('http://localhost:18090/actividad-diaria');
  await page.locator('#description').fill('AUD-ACT Original');
  await page.locator('#typeId').selectOption({label:'Revisión'});
  await page.getByRole('button',{name:'Agregar actividad'}).click();
  const activity=page.locator('main li').filter({hasText:'AUD-ACT Original'});
  await activity.getByText('Corregir',{exact:true}).click();
  await activity.locator('textarea').fill('AUD-ACT Corrección conservada');
  await activity.locator('input[name=otherType]').fill('Otro tipo de prueba');
  await activity.getByRole('button',{name:'Guardar corrección'}).click();
  out.activityValidation={url:page.url(),errors:await page.locator('p.error').allTextContents(),creationText:await page.locator('#description').inputValue(),editTexts:await page.locator('details textarea').allTextContents()};
  await page.screenshot({path:'target/auditoria-2026-09-26/actividad-error-edicion.png',fullPage:true});
  await page.locator('#typeId').selectOption('');
  await page.getByRole('button',{name:'Agregar actividad'}).click();
  out.activityValidation.afterRetry=await page.locator('main li').allTextContents();

  await page.goto('http://localhost:18090/pendientes/09323cc0-b6bf-4973-be32-2f58dccff205');
  await page.getByRole('button',{name:'Marcar como cumplido',exact:true}).click();
  out.complete={url:page.url(),fulfilledBanner:await page.locator('p[role=alert]').allTextContents()};
  await page.locator('#motivoReversion').fill('Auditoría: comprobar reversión');
  await page.getByRole('button',{name:'Revertir cumplimiento'}).click();
  out.revert={completeButton:await page.getByRole('button',{name:'Marcar como cumplido'}).count()};
  await page.getByRole('button',{name:'No cumplido, pasar al siguiente día hábil'}).click();
  out.reschedule=await page.locator('tr').filter({has:page.getByRole('rowheader',{name:'Fecha programada',exact:true})}).innerText();
  await page.goto('http://localhost:18090/pendientes?q=AUD-PEND-001&alerta=proximos');
  out.filterBefore=page.url();
  if(await page.getByRole('link',{name:'AUD-PEND-001 Revisar contrato',exact:true}).count()) {
    await page.getByRole('link',{name:'AUD-PEND-001 Revisar contrato',exact:true}).click();
    await page.getByRole('link',{name:'← Volver a pendientes'}).click();
    out.filterAfter=page.url();
  }
  return out;
}
