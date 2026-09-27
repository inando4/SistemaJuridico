async (page) => {
  await page.setViewportSize({width:1440,height:1000});
  if(await page.locator('#modo-presentacion').getAttribute('aria-checked')==='false') await page.locator('#modo-presentacion').click();
  const out={};
  for(const [route,field,duplicate,corrected] of [['judiciales','caseNumber','AUD-JUD-001','AUD-JUD-RESP-002'],['administrativos','fileNumber','AUD-ADM-001','AUD-ADM-RESP-002']]) {
    await page.goto('http://localhost:18090/'+route+'/nuevo');
    await page.locator('#ownerId').selectOption('22222222-2222-4222-8222-222222222222');
    await page.locator('#'+field).fill(duplicate);
    await page.getByRole('button',{name:'Guardar',exact:true}).click();
    const error={ownerSelectorCount:await page.locator('#ownerId').count(),errors:await page.locator('p.error').allTextContents()};
    await page.screenshot({path:'target/auditoria-2026-09-26/'+route+'-responsable-perdido.png',fullPage:false});
    await page.locator('#'+field).fill(corrected);
    await page.getByRole('button',{name:'Guardar',exact:true}).click();
    out[route]={error,resultUrl:page.url(),resultOwner:await page.locator('p.tenue').first().innerText()};
  }
  await page.goto('http://localhost:18090/pendientes/nuevo');
  await page.locator('#title').fill('AUD-VALIDACION-DESCRIPCION');
  await page.locator('#description').fill('A'.repeat(10001));
  await page.getByRole('button',{name:'Guardar',exact:true}).click();
  out.longDescription={url:page.url(),visibleErrors:await page.locator('p.error').allTextContents(),descriptionLength:(await page.locator('#description').inputValue()).length,ariaInvalid:await page.locator('#description').getAttribute('aria-invalid'),ariaDescribedby:await page.locator('#description').getAttribute('aria-describedby')};
  await page.screenshot({path:'target/auditoria-2026-09-26/validacion-sin-campo.png',fullPage:false});
  return out;
}
