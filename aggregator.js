/* TerrainScope V18 — Liste unique, sans stockage d'annonces.
   Sources: uniquement les extraits que Google Programmable Search affiche officiellement.
   Aucun appel direct aux portails tiers, aucune cle API exposee. */
(function(){
  'use strict';
  const SOURCES=[
    {name:'Le Bon Coin',domain:'leboncoin.fr'},
    {name:'SeLoger',domain:'seloger.com'},
    {name:'Bien’ici',domain:'bienici.com'},
    {name:'IAD',domain:'iadfrance.fr'},
    {name:'Autres sites et lotisseurs',domain:null}
  ];
  const MAX_PAGES_PER_SOURCE=3;
  const PAGE_SIZE=10;
  const MAX_STAGE_MS=12000;
  const STOP=new Set([
    'terrain','terrains','vendre','vente','vendu','acheter','achat','batir',
    'constructible','constructibles','parcelle','parcelles','annonce','annonces',
    'immobilier','immobiliere','ideal','pour','dans','avec','sans','zone','prix',
    'm2','sur','une','des','les','aux','chez','proche','secteur','jardin',
    'vente','exclusivite','nouveau','nouveaute','centre','france','terrainabatir',
    'habitation','description','disponible','situe','situee','jolie','beau'
  ]);
  const state={serial:0,stage:0,active:false,waiting:1,pageSeen:new Set(),
    byUrl:new Set(),entries:[],unique:[],duplicates:0,page:1,
    stageTimer:null,pageTimer:null,processing:false,started:false};
  const get=id=>document.getElementById(id);
  const normalized=value=>String(value||'').normalize('NFD')
    .replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[’']/g,' ')
    .replace(/[^a-z0-9]+/g,' ').replace(/\s+/g,' ').trim();
  const domainOf=url=>{
    try{return new URL(url).hostname.replace(/^www\./,'').toLowerCase()}catch(e){return ''}
  };
  const rank=url=>{
    const domain=domainOf(url);
    const n=SOURCES.findIndex(x=>x.domain&&(domain===x.domain||domain.endsWith('.'+x.domain)));
    if(n>=0)return n;
    const dev=DEVELOPERS.findIndex(x=>domain===x.domain||domain.endsWith('.'+x.domain));
    return dev>=0?SOURCES.length-1:SOURCES.length;
  };
  const sourceName=url=>{
    const r=rank(url);
    if(r===SOURCES.length-1){
      const host=domainOf(url),dev=DEVELOPERS.find(x=>host===x.domain||host.endsWith('.'+x.domain));
      if(dev)return dev.name;
    }
    return r<SOURCES.length?SOURCES[r].name:domainOf(url);
  };
  const sameUrl=(a,b)=>canonicalListingUrl(a)===canonicalListingUrl(b);
  const cityFor=(title,description)=>{
    const cities=lastSearch?.cities||[];
    for(const c of cities)if(containsPlace(title,c))return c;
    for(const c of cities)if(containsPlace(description,c))return c;
    return cities.length===1?cities[0]:'';
  };
  const tokens=(s,city)=>{
    const exclusion=new Set([...STOP,...normalized(city).split(' ')]);
    return new Set(normalized(s).split(' ').filter(w=>
      w.length>3&&!exclusion.has(w)&&!/^\d+$/.test(w)
    ));
  };
  const shared=(a,b)=>{
    let count=0;
    for(const value of a)if(b.has(value))count++;
    return count;
  };
  const address=s=>{
    const m=normalized(s).match(/\b(rue|impasse|allee|avenue|chemin|route|lieu dit|boulevard)\s+(?:(?:de|des|du|la|le|l|aux)\s+)*([a-z0-9]{4,})\b/);
    return m?m[1]+':'+m[2]:'';
  };
  function probableDuplicate(a,b){
    if(sameUrl(a.url,b.url))return true;
    if(!a.city||!b.city||normalized(a.city)!==normalized(b.city))return false;
    // Ni prix ni surface: trop ambigu pour fusionner deux terrains.
    if(!a.price||!b.price||!a.area||!b.area)return false;
    if(Math.abs(a.area-b.area)>Math.max(1,a.area*.01))return false;
    if(Math.abs(a.price-b.price)>Math.max(300,a.price*.01))return false;
    const aAddress=address(a.title+' '+a.description);
    const bAddress=address(b.title+' '+b.description);
    if(aAddress&&bAddress&&aAddress===bAddress)return true;
    if(aAddress&&bAddress&&aAddress!==bAddress)return false;
    const aTokens=tokens(a.title+' '+a.description,a.city);
    const bTokens=tokens(b.title+' '+b.description,b.city);
    const common=shared(aTokens,bTokens);
    // Deux lots du meme lotissement peuvent avoir le meme prix et la meme surface.
    // Exiger au moins deux mots distinctifs en commun pour ne pas les effacer.
    return common>=2 && common/Math.min(aTokens.size||1,bTokens.size||1)>=.45;
  }
  function candidate(node){
    const a=node.querySelector('a.gs-title'),url=a?.href||'';
    if(!url||!/^https?:/.test(url))return null;
    const title=(a.textContent||'Terrain à bâtir').replace(/\s+/g,' ').trim();
    const description=(node.querySelector('.gs-snippet')?.textContent||'').replace(/\s+/g,' ').trim();
    if(!title)return null;
    const kind=classifyResult(node);
    if(kind==='constructeurs'||node.dataset.clientHidden==='true')return null;
    const city=cityFor(title,description);
    // Si Google nomme clairement une commune differente, la garder hors de la selection.
    if(node.dataset.locationStatus==='nearby')return null;
    const price=getPrice(title+' '+description);
    const area=getArea(title+' '+description);
    return {url,title,description,city,price,area,source:sourceName(url),
      rank:rank(url),otherLinks:[]};
  }
  function mergeCandidates(){
    const items=[...state.entries].sort((a,b)=>
      a.rank-b.rank||(a.price??Infinity)-(b.price??Infinity)||a.title.localeCompare(b.title,'fr'));
    const uniques=[];
    let duplicateCount=0;
    for(const item of items){
      const old=uniques.find(other=>probableDuplicate(item,other));
      if(old){
        duplicateCount++;
        if(![old.url,...old.otherLinks.map(x=>x.url)].some(url=>sameUrl(url,item.url))){
          old.otherLinks.push({url:item.url,source:item.source});
        }
      }else{
        uniques.push({...item,otherLinks:[]});
      }
    }
    state.unique=uniques;
    state.duplicates=duplicateCount;
  }
  const money=n=>new Intl.NumberFormat('fr-FR').format(n)+' €';
  const areaText=n=>new Intl.NumberFormat('fr-FR').format(n)+' m²';
  const cardLink=(url,text,css)=>{
    const a=document.createElement('a');
    a.href=url;a.target='_blank';a.rel='noopener noreferrer';a.className=css;
    a.textContent=text;return a;
  };
  function draw(){
    const out=get('uniqueResults'),summary=get('uniqueSummary'),pages=get('uniquePages');
    if(!out||!summary||!pages)return;
    const unique=state.unique,number=unique.length;
    const pagesCount=Math.max(1,Math.ceil(number/PAGE_SIZE));
    state.page=Math.min(state.page,pagesCount);
    const status=state.active?'Recherche en cours · '+SOURCES[state.stage]?.name:
      'Recherche Google terminée';
    summary.textContent=number+' terrain(s) distinct(s) repéré(s) · '+state.duplicates+
      ' doublon(s) regroupé(s) · '+status;
    out.replaceChildren();
    if(!number){
      const p=document.createElement('p');p.className='unique-empty';
      p.textContent=state.active?'Recherche des terrains sur les portails…':
        'Aucune annonce exploitable trouvée dans les résultats Google. Essaie les accès directs aux sites ci-dessous.';
      out.append(p);pages.hidden=true;return;
    }
    const from=(state.page-1)*PAGE_SIZE;
    for(const item of unique.slice(from,from+PAGE_SIZE)){
      const card=document.createElement('article');card.className='unique-card';
      const header=document.createElement('div');header.className='unique-card-header';
      const source=document.createElement('strong');source.className='unique-source';
      source.textContent=item.source;
      const price=document.createElement('strong');price.className='unique-price';
      price.textContent=item.price?money(item.price):'Prix à consulter';
      header.append(source,price);
      const title=document.createElement('h4');title.textContent=item.title;
      const details=document.createElement('p');details.className='unique-details';
      details.textContent=[item.city||'Localisation à confirmer',
        item.area?areaText(item.area):'Surface à consulter'].join(' · ');
      const actions=document.createElement('div');actions.className='unique-actions';
      actions.append(cardLink(item.url,'Voir ce terrain ↗','unique-open'));
      if(item.otherLinks.length){
        const count=document.createElement('span');count.className='unique-shared';
        count.textContent='Aussi publié sur '+[...new Set(item.otherLinks.map(x=>x.source))].join(', ');
        actions.append(count);
      }
      card.append(header,title,details,actions);
      out.append(card);
    }
    pages.replaceChildren();
    pages.hidden=pagesCount<=1;
    if(pagesCount<=1)return;
    const title=document.createElement('div');title.className='unique-page-label';
    title.textContent='Page '+state.page+' sur '+pagesCount+' · '+number+' terrains distincts repérés';
    pages.append(title);
    const buttons=document.createElement('div');buttons.className='unique-page-buttons';
    function button(text,page){
      const b=document.createElement('button');b.type='button';b.textContent=text;
      b.disabled=page===state.page;b.className=page===state.page?'active':'';
      if(b.disabled)b.setAttribute('aria-current','page');
      b.onclick=()=>{state.page=page;draw();get('uniquePanel').scrollIntoView({behavior:'smooth',block:'start'})};
      buttons.append(b);
    }
    if(state.page>1)button('←',state.page-1);
    for(let p=1;p<=pagesCount;p++)button(String(p),p);
    if(state.page<pagesCount)button('→',state.page+1);
    pages.append(buttons);
  }
  function stopTimers(){
    clearTimeout(state.stageTimer);clearTimeout(state.pageTimer);
    state.stageTimer=null;state.pageTimer=null;
  }
  function finish(){
    stopTimers();state.active=false;draw();
  }
  function nextStage(){
    stopTimers();
    if(!state.active)return;
    state.stage++;state.pageSeen.clear();state.waiting=1;state.processing=false;state.started=false;
    if(state.stage>=SOURCES.length){finish();return}
    startStage();
  }
  const engine=()=>window.google?.search?.cse?.element?.getElement?.('terrains');
  function startStage(tries=0){
    if(!state.active||state.started)return;
    const g=engine();
    if(!g){
      if(tries<25){setTimeout(()=>startStage(tries+1),350);return}
      finish();return;
    }
    const item=SOURCES[state.stage],cities=lastSearch?.cities||[];
    const q=query(cities)+(item.domain?' site:'+item.domain:'');
    state.started=true;
    state.stageTimer=setTimeout(()=>nextStage(),MAX_STAGE_MS);
    get('uniquePanel').hidden=false;
    draw();
    try{g.execute(q)}catch(e){nextStage()}
  }
  function googlePages(){
    const pages=[];
    for(const elt of get('google-results').querySelectorAll('.gsc-cursor-page')){
      const n=Number((elt.textContent||'').trim());
      if(Number.isInteger(n)&&n>0&&n<=100&&!pages.some(p=>p.n===n)){
        pages.push({n,elt,current:elt.classList.contains('gsc-cursor-current-page')});
      }
    }
    return pages.sort((a,b)=>a.n-b.n);
  }
  function capture(serial){
    if(serial!==state.serial||!state.active||state.processing)return;
    const pages=googlePages();
    const current=pages.find(p=>p.current)?.n||state.waiting;
    if(current!==state.waiting)return;
    if(state.pageSeen.has(current))return;
    state.pageSeen.add(current);
    const nodes=[...get('google-results').querySelectorAll('.gsc-webResult.gsc-result')];
    for(const node of nodes){
      const item=candidate(node);
      if(!item)continue;
      const url=canonicalListingUrl(item.url);
      if(!url||state.byUrl.has(url))continue;
      state.byUrl.add(url);
      state.entries.push(item);
    }
    mergeCandidates();draw();
    clearTimeout(state.pageTimer);
    if(state.pageSeen.size>=MAX_PAGES_PER_SOURCE){nextStage();return}
    const next=pages.find(p=>p.n>current&&!state.pageSeen.has(p.n));
    if(!next){nextStage();return}
    state.processing=true;
    setTimeout(()=>{
      if(serial!==state.serial||!state.active)return;
      state.processing=false;
      state.waiting=next.n;
      state.pageTimer=setTimeout(()=>nextStage(),4500);
      try{
        const target=googlePages().find(p=>p.n===state.waiting)?.elt;
        if(!target){nextStage();return}
        target.click();
      }catch(e){nextStage()}
    },380);
  }
  function search(cities){
    state.serial++;stopTimers();
    state.entries=[];state.unique=[];state.byUrl.clear();
    state.duplicates=0;state.page=1;state.stage=0;state.waiting=1;
    state.pageSeen.clear();state.active=true;state.processing=false;state.started=false;
    get('uniquePanel').hidden=false;draw();startStage();
  }
  function onRendered(){
    if(!state.active)return;
    const serial=state.serial;
    setTimeout(()=>capture(serial),320);
  }
  window.TerrainAggregator={search,onRendered,resume:()=>{if(state.active)startStage()}};
  draw();
})();
