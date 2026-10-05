import { App } from '@modelcontextprotocol/ext-apps';
import cytoscape from 'cytoscape';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import './app.css';
const app=new App({name:'inventory-simulation-app',version:'0.1.0'},{});
let cleanup=()=>{};
app.ontoolresult=result=>{
  cleanup();const data=result.structuredContent.data;
  const detail=document.getElementById('detail');const viz=document.getElementById('viz');viz.replaceChildren();
  detail.textContent='SIMULATED '+data.sku+' · click a node or warehouse for details';
  document.getElementById('note').textContent=data.kind==='spatial'
    ?'SIMULATION · Coordinate grid, not an external basemap. Connections are not driving routes.'
    :'SIMULATION · Cytoscape renders fixture graph nodes and edges, not a generated image or live database query.';
  if(data.kind==='graph'){
    const cy=cytoscape({container:viz,elements:[...data.nodes,...data.edges],layout:{name:'breadthfirst',directed:true,padding:65},
      style:[{selector:'node',style:{label:'data(label)','background-color':'#167968',color:'#172735','font-size':14,'text-wrap':'wrap','text-max-width':140}},
      {selector:'edge',style:{width:3,'line-color':'#52789a','target-arrow-color':'#52789a','target-arrow-shape':'triangle','curve-style':'bezier'}}]});
    cy.on('tap','node',e=>detail.textContent=e.target.data('label')+' · SIMULATED '+data.sku);cleanup=()=>cy.destroy();
  }else if(data.kind==='spatial'){
    const grid=[];
    for(let x=-180;x<=180;x+=10)grid.push({type:'Feature',properties:{},geometry:{type:'LineString',coordinates:[[x,-80],[x,80]]}});
    for(let y=-80;y<=80;y+=10)grid.push({type:'Feature',properties:{},geometry:{type:'LineString',coordinates:[[-180,y],[180,y]]}});
    const map=new maplibregl.Map({container:viz,attributionControl:false,style:{version:8,sources:{grid:{type:'geojson',data:{type:'FeatureCollection',features:grid}},inventory:{type:'geojson',data:data.geojson}},layers:[
      {id:'background',type:'background',paint:{'background-color':'#e6f0f3'}},
      {id:'grid',type:'line',source:'grid',paint:{'line-color':'#bbd1d9','line-width':1}},
      {id:'connection',type:'line',source:'inventory',filter:['==',['geometry-type'],'LineString'],paint:{'line-color':'#176dab','line-width':4}}
    ]},center:[0,0],zoom:1});
    const bounds=new maplibregl.LngLatBounds();
    for(const feature of data.geojson.features.filter(f=>f.geometry.type==='Point')){
      const p=feature.properties, coordinates=feature.geometry.coordinates;bounds.extend(coordinates);
      const button=document.createElement('button');button.className='warehouse';button.setAttribute('aria-label',p.name+' warehouse');
      button.style.background=p.role==='source'?'#2d8035':'#bd4935';
      const marker=new maplibregl.Marker({element:button}).setLngLat(coordinates).addTo(map);
      button.onclick=event=>{event.stopPropagation();detail.textContent=p.name+' · '+p.role+' · risk '+p.risk+' (0–1)';
        new maplibregl.Popup().setLngLat(coordinates).setText(detail.textContent).addTo(map);};
    }
    map.fitBounds(bounds,{padding:75,duration:0});map.addControl(new maplibregl.NavigationControl());
    map.on('error',e=>detail.textContent='Map error: '+e.error.message);
    cleanup=()=>map.remove();
  }
};
app.connect().catch(e=>document.getElementById('detail').textContent='Host connection failed: '+e.message);
