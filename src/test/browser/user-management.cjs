// Requires Playwright and a local test deployment with the seeded administrator.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { randomUUID } = require('node:crypto');
const base = process.env.DARKHOLD_TEST_URL || 'http://localhost:8181';
const output = process.env.DARKHOLD_TEST_OUTPUT || '/tmp/darkhold-user-management';

(async () => {
    fs.mkdirSync(output, {recursive:true});
    const browser = await chromium.launch({headless:true, executablePath:process.env.CHROMIUM_PATH || undefined});
    const admin = await browser.newPage({serviceWorkers:'block', viewport:{width:1440,height:1000}});
    const guest = await browser.newPage({serviceWorkers:'block'});
    const email = `permissions-${randomUUID()}@example.invalid`;
    const password = randomUUID();
    const errors = [];
    [admin,guest].forEach(page=>page.on('pageerror',error=>errors.push(error.message)));
    let userId;
    async function login(page, username, secret) {
        await page.goto(base+'/login');
        await page.locator('[name=username]').fill(username);
        await page.locator('[name=password]').fill(secret);
        await Promise.all([page.waitForURL(base+'/'),page.locator('button[type=submit]').click()]);
    }
    async function post(page, path, data={}) {
        return page.evaluate(async ({path,data})=>{
            const csrf=document.cookie.split('; ').find(cookie=>cookie.startsWith('XSRF-TOKEN='))?.split('=')[1];
            const response=await fetch(path,{method:'POST',headers:{'X-XSRF-TOKEN':csrf},body:new URLSearchParams(data)});
            return response.status;
        },{path,data});
    }
    function luminance(color) {
        const rgb=color.match(/[\d.]+/g).slice(0,3).map(Number).map(x=>x/255)
            .map(x=>x<=0.04045 ? x/12.92 : ((x+0.055)/1.055)**2.4);
        return rgb[0]*0.2126+rgb[1]*0.7152+rgb[2]*0.0722;
    }
    try {
        await guest.goto(base+'/registration');
        await guest.locator('[name=firstName]').fill('Permission');
        await guest.locator('[name=lastName]').fill('Browser Test');
        await guest.locator('[name=email]').fill(email);
        await guest.locator('[name=password]').fill(password);
        await Promise.all([guest.waitForEvent('load'),guest.locator('button[type=submit]').click()]);
        await login(admin,process.env.DARKHOLD_TEST_USERNAME || 'admin@admin.com',process.env.DARKHOLD_TEST_PASSWORD || 'admin');
        await admin.goto(base+'/userManagement?lang=de');
        const lastPage=Number((await admin.locator('.admin-pagination-controls a').last().getAttribute('href')).split('/').pop());
        for(let page=1;page<=lastPage;page++) {
            await admin.goto(base+'/user/page/'+page);
            const row=admin.locator('tbody tr').filter({hasText:email});
            if(await row.count()) {
                userId=await row.locator('[data-user-id]').getAttribute('data-user-id');
                assert.match(await row.innerText(),/GUEST/);
                break;
            }
        }
        assert.ok(userId,'The test account must exist with the GUEST role');
        await login(guest,email,password);
        await guest.goto(base+'/options');
        assert.equal(await guest.locator('[onclick="manageUsers();"]').count(),0);
        assert.equal(await guest.locator('a[href="/admin"]').count(),0);
        for(const path of ['/admin','/userManagement','/user/create','/user/edit/'+userId,'/user/page/1']) {
            const response=await guest.request.get(base+path);
            assert.equal(response.status(),403,path);
        }
        for(const path of ['/users/save','/user/delete/'+userId,'/users/'+userId+'/enabled/false']) {
            assert.equal(await post(guest,path,{id:userId,roles:'1'}),403,path);
        }
        const button=admin.locator(`[data-user-id="${userId}"]`);
        await button.click();
        await admin.locator('#confirmModal').waitFor({state:'visible'});
        assert.match(await admin.locator('#confirmText').innerText(),/Möchtest du das Benutzerkonto/);
        assert.match(await admin.locator('#confirmText').innerText(),new RegExp(userId));
        assert.equal(await admin.locator('#deleteUserForm').getAttribute('method'),'post');
        assert.equal(await admin.locator('#deleteUserForm [name=_csrf]').count(),1);
        for(const theme of ['light','dark']) {
            await admin.evaluate(theme=>ThemeManager.setTheme(theme),theme);
            const colors=await admin.locator('#confirmModal').evaluate(modal=>({
                text:getComputedStyle(modal.querySelector('#confirmText')).color,
                background:getComputedStyle(modal.querySelector('.modal-content')).backgroundColor
            }));
            const values=[luminance(colors.text),luminance(colors.background)].sort((a,b)=>b-a);
            assert.ok((values[0]+0.05)/(values[1]+0.05)>=4.5,theme+' dialog text must be readable');
            await admin.screenshot({path:output+`/delete-${theme}.png`,fullPage:true,animations:'disabled'});
        }
        await admin.setViewportSize({width:390,height:844});
        await admin.screenshot({path:output+'/delete-mobile-dark.png',fullPage:true,animations:'disabled'});
        const box=await admin.locator('#confirmModal .modal-content').boundingBox();
        assert.ok(box.x>=0 && box.x+box.width<=390);
        await admin.locator('#confirmModal .btn-secondary').click();
        await admin.locator('#confirmModal').waitFor({state:'hidden'});
        assert.equal(await button.count(),1,'Cancel must leave the account intact');
        await button.click();
        const [response]=await Promise.all([
            admin.waitForResponse(response=>new URL(response.url()).pathname==='/user/delete/'+userId),
            admin.locator('#deleteUserForm button[type=submit]').click()
        ]);
        assert.equal(response.request().method(),'POST');
        assert.equal(response.status(),302);
        await admin.waitForURL('**/userManagement');
        const deleted=await admin.request.get(base+'/user/edit/'+userId,{maxRedirects:0});
        assert.equal(deleted.status(),302);
        assert.deepEqual(errors,[]);
        console.log('PASS: real GUEST login cannot manage accounts or forge role changes; administrator deletion uses CSRF-protected POST; German confirmation is readable in light/dark and fits mobile; cancel preserves the account.');
        userId=null;
    } finally {
        if(userId) await post(admin,'/user/delete/'+userId);
        await browser.close();
    }
})().catch(error=>{console.error(error);process.exitCode=1;});
