async (page) => {
 const results=[];
 for (const route of ['/', '/pendientes', '/pendientes/nuevo', '/cumplidos', '/calendario', '/configuracion']) {
  await page.goto('http://localhost:18090'+route);
  for (const width of [1440,320]) {
   await page.setViewportSize({width,height:900});
   results.push({route,...await page.evaluate(()=>{
    function rgb(s){return (s.match(/[\d.]+/g)||[]).map(Number);}
    function lum(a){return a.slice(0,3).map(n=>n/255).map(n=>n<=.04045?n/12.92:Math.pow((n+.055)/1.055,2.4)).reduce((s,n,i)=>s+n*[.2126,.7152,.0722][i],0);}
    function bg(e){const chain=[];for(let p=e;p;p=p.parentElement)chain.unshift(p);return chain.reduce((b,p)=>{const c=rgb(getComputedStyle(p).backgroundColor),a=c[3]??1;return b.map((n,i)=>c[i]*a+n*(1-a))},[255,255,255]);}
    const failures=[];let checked=0;
    for(const el of document.querySelectorAll('body *')) {
      if(![...el.childNodes].some(n=>n.nodeType===3&&n.textContent.trim())||!el.getClientRects().length)continue;
      const s=getComputedStyle(el);if(s.visibility==='hidden'||s.display==='none')continue;
      const fg=rgb(s.color),b=bg(el);if(fg.length<3)continue;
      const l1=lum(fg),l2=lum(b),ratio=(Math.max(l1,l2)+.05)/(Math.min(l1,l2)+.05);
      const large=parseFloat(s.fontSize)>=24 ||(parseFloat(s.fontSize)>=18.66 && parseInt(s.fontWeight)>=700);
      checked++;if(ratio<(large?3:4.5))failures.push({text:el.textContent.trim().slice(0,70),ratio:+ratio.toFixed(2),fg:s.color,bg:b,font:s.fontSize});
    }
    return {width:innerWidth,docWidth:document.documentElement.scrollWidth,checked,contrastFailures:failures,smallButtons:[...document.querySelectorAll('button')].filter(e=>e.getClientRects().length).map(e=>({text:e.textContent.trim().slice(0,50),height:Math.round(e.getBoundingClientRect().height)})).filter(e=>e.height<44),firstTableHeaders:[...document.querySelectorAll('thead th')].slice(0,10).map(e=>({text:e.textContent.trim(),width:Math.round(e.getBoundingClientRect().width),height:Math.round(e.getBoundingClientRect().height)})),hasSkipLink:!!document.querySelector('a[href="#main"],a[href="#contenido"]')};
   })});
  }
 }
 await page.setViewportSize({width:1440,height:1000});
 await page.goto('http://localhost:18090/pendientes/nuevo');
 await page.locator('#title').fill('Borrador preservado');
 await page.locator('#modo-presentacion').focus();
 await page.keyboard.press('Space');
 const preserved=await page.locator('#title').inputValue();
 await page.keyboard.press('Enter');
 await page.locator('#title').focus();await page.keyboard.press('Tab');
 const keyboard=await page.evaluate(()=>({focused:document.activeElement.id,outline:getComputedStyle(document.activeElement).outline}));
 return {results,presentationDraft:preserved,keyboard};
}
