const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright-core');
(async () => {
 // 使用构建产物测试，API 为固定夹具，不连接真实业务链。
 const { spawn } = require('node:child_process');
 const path = require('node:path');
 const server = spawn(process.execPath, [path.join(__dirname, '../node_modules/vite/bin/vite.js'),
   'preview', '--host', '127.0.0.1', '--port', '5174', '--strictPort'],
   { cwd: path.join(__dirname, '..'), stdio: 'inherit', windowsHide: true });
 let serverFailed = false;
 server.once('exit', () => { serverFailed = true; });
 try {
 let ready = false;
 for (let attempt = 0; attempt < 50; attempt++) {
   if (serverFailed) throw new Error('测试服务启动失败');
   try { ready = (await fetch('http://127.0.0.1:5174', {signal:AbortSignal.timeout(1000)})).ok; } catch {}
   if (ready) break;
   await new Promise(resolve => setTimeout(resolve, 200));
 }
 if (!ready) throw new Error('测试服务未就绪');
 const browser = await chromium.launch({executablePath:process.env.E2E_BROWSER_PATH,headless:true,timeout:15000});
 const out=(process.env.E2E_ARTIFACT_DIR || require('node:path').join(require('node:os').tmpdir(),'foodtrace-e2e'))+'/';
 require('node:fs').mkdirSync(out,{recursive:true});
 let page;
 try {
 const context = await browser.newContext({viewport:{width:1280,height:900}});
 page = await context.newPage();
 page.setDefaultTimeout(10000);
 const errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 const bytes=Buffer.from('食品检测报告：合格');
 const hash=require('node:crypto').createHash('sha256').update(bytes).digest('hex');
 const product={id:1,name:'阳光草莓',batchNo:'QA-001',originFarm:'0xfarm',currentHolder:'0xinsp',stage:2,recalled:false,records:[{stage:0,operator:'0xfarm',location:'云南昆明',description:'首批采摘',timestamp:1791691200000,dataHash:'sha256:'+hash},{stage:2,operator:'0xinsp',location:'合肥检测中心',description:'农残检测合格',timestamp:1791694800000,dataHash:'file:sha256:'+hash},{stage:1,operator:'0xprocessing',location:'昆明',description:'旧版记录',timestamp:1791692400000,dataHash:hash}]};
 let mode='ok',submitted=null;
 await page.route('**/api/**',async route=>{
 const url=new URL(route.request().url());
 // Vite 源码模块 /src/api/ 不属于后端请求。
 if(!url.pathname.startsWith('/api/')) return route.continue();
 let data=product;
 if(url.pathname==='/api/storage/upload')data={sha256:hash,filename:'报告.txt',size:bytes.length};
 else if(url.pathname.startsWith('/api/storage/'))return route.fulfill({status:mode==='missing'?404:200,contentType:'application/octet-stream',body:mode==='tampered'?Buffer.from('tampered'):bytes});
 else if(url.pathname==='/api/orgs') data=[];
 else if(url.pathname.endsWith('/inspect'))submitted=route.request().postDataJSON();
 await route.fulfill({json:{code:0,data,message:'ok'}});
 });
 console.log('navigate');
 await page.goto('http://127.0.0.1:5174/trace?batchNo=QA-001');
 await page.getByText('阳光草莓',{exact:true}).waitFor();
 await page.getByRole('button',{name:'核验原文'}).click();
 await page.getByText('核验通过：文件与链上哈希一致').waitFor();
 const assert=require('node:assert/strict');
 assert.equal(await page.getByRole('link',{name:'下载原文'}).count(),1);
 assert.equal(await page.locator('vite-error-overlay').count(),0);

 await page.screenshot({path:out+'evidence-desktop.png',fullPage:true});
 console.log({url:page.url(),title:await page.title(),desktop:'pass'});
 mode='tampered';
 await page.getByRole('button',{name:'核验原文'}).click();
 await page.getByText('核验失败：文件与链上哈希不符').waitFor();
 mode='missing';
 await page.getByRole('button',{name:'核验原文'}).click();
 await page.getByText('原文缺失，无法核验').waitFor();
 await page.setViewportSize({width:390,height:844});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await page.screenshot({path:out+'evidence-mobile.png',fullPage:true});
 console.log('mobile and failure states: pass');
 await page.evaluate(()=>{localStorage.setItem('ft_token','qa');localStorage.setItem('ft_user',JSON.stringify({id:3,username:'inspector',orgName:'合肥检测中心',role:3,regulator:false,chainAddress:'0xinsp',status:1}));});
 await page.goto('http://127.0.0.1:5174/products/1');
 await page.getByRole('button',{name:'质检裁决',exact:true}).click();
 const submit=page.getByRole('button',{name:'提交裁决',exact:true});
 assert.equal(await submit.isDisabled(),true);
 await page.getByLabel('检测报告',{exact:true}).setInputFiles({name:'报告.txt',mimeType:'text/plain',buffer:bytes});
 await page.getByText('存证已上传：报告.txt').waitFor();
 assert.equal(await submit.isEnabled(),true);
 await page.waitForTimeout(350);
 await page.screenshot({path:out+'inspection-mobile.png'});
 await submit.click();
 await page.getByText('质检合格,已上链').waitFor();
 assert.equal(submitted.reportHash,'file:sha256:'+hash);
 assert.equal(await page.locator('vite-error-overlay').count(),0);
 assert.deepEqual(errors,[]);
 console.log({inspect:'pass',errors});
 } catch (error) {
   if (page) await page.screenshot({path:out+'failure.png',fullPage:true}).catch(() => {});
   throw error;
 } finally { await browser.close(); }
 } finally { server.kill(); }
})().catch(e=>{console.error(e);process.exitCode=1;});