const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = process.env.DARKHOLD_TEST_URL || 'http://localhost:8181';
const output = process.env.DARKHOLD_TEST_OUTPUT || '/tmp/darkhold-german';
(async () => {
    fs.mkdirSync(output, {recursive: true});
    const browser = await chromium.launch({headless: true, executablePath: process.env.CHROMIUM_PATH || undefined});
    const host = await browser.newPage({serviceWorkers:'block', viewport:{width:1440,height:1050}});
    const guest = await browser.newPage({serviceWorkers:'block', viewport:{width:390,height:844}});
    const errors = [];
    [host,guest].forEach(page => page.on('pageerror', error => errors.push(error.message)));
    let quizId, pin;
    async function selectLanguage(page, language) {
        await page.locator('#languageDropdown:visible, #languageDropdownAnon:visible').click();
        await Promise.all([page.waitForURL(url => url.searchParams.get('lang') === language),
            page.locator(`.language-dropdown:visible [onclick*="'${language}'"]`).click()]);
        assert.equal(await page.locator('html').getAttribute('lang'), language);
        assert.equal(await page.locator('.dh-lang-code:visible').textContent(), language.toUpperCase());
    }
    try {
        await host.goto(base + '/');
        await host.evaluate(() => localStorage.setItem('darkhold_language', 'es'));
        await host.reload();
        assert.equal(await host.locator('.dh-lang-code:visible').textContent(), 'EN');
        await selectLanguage(host, 'de');
        await host.getByRole('heading',{name:'In Sekunden startklar'}).waitFor();
        await host.screenshot({path:output+'/home-de.png',fullPage:true,animations:'disabled'});
        await host.goto(base + '/join');
        await host.getByText('Wähle deinen Avatar',{exact:true}).waitFor();
        await host.goto(base + '/login');
        assert.equal(await host.locator('html').getAttribute('lang'), 'de');
        await host.locator('[name=username]').fill(process.env.DARKHOLD_TEST_USERNAME || 'admin@admin.com');
        await host.locator('[name=password]').fill(process.env.DARKHOLD_TEST_PASSWORD || 'admin');
        await Promise.all([host.waitForURL(base+'/'), host.locator('button[type=submit]').click()]);
        assert.equal(await host.locator('.dh-lang-code:visible').textContent(), 'DE');
        await selectLanguage(host, 'en');
        await selectLanguage(host, 'de');
        await host.goto(base + '/challenge/create');
        await host.getByRole('heading',{name:'Grundinformationen'}).waitFor();
        await host.locator('#title').fill('German smoke '+Date.now());
        await host.locator('#description').fill('Unveränderter Quizinhalt');
        await host.locator('#btnSubmit').click();
        const edit = host.getByRole('link',{name:'Fragen hinzufügen'});
        await edit.waitFor();
        quizId = (await edit.getAttribute('href')).split('/').pop();
        await edit.click();
        await host.getByRole('button',{name:/Frage Hinzufügen/}).click();
        await host.locator('#questionModal').waitFor({state:'visible'});
        assert.equal(await host.locator('#modalTitle').textContent(),'Frage hinzufügen');
        await host.screenshot({path:output+'/editor-de.png',fullPage:true,animations:'disabled'});
        await host.locator('#questionText').fill('Was ist 2 + 2?');
        await host.locator('#answer1').fill('4');
        await host.locator('#answer2').fill('3');
        await host.locator('#correctA').check();
        await Promise.all([host.waitForEvent('load'), host.locator('#questionModal .btn-primary').click()]);
        await selectLanguage(host, 'en');
        assert.match(await host.locator('#questionList').textContent(),/Was ist 2 \+ 2\?/);
        await selectLanguage(host, 'de');
        await Promise.all([host.waitForURL('**/play/*'), host.getByRole('button',{name:'Live-Quiz starten'}).click()]);
        pin = new URL(host.url()).pathname.split('/').pop();
        fs.writeFileSync(output+'/fixture.json',JSON.stringify({pin,challengeId:quizId}));
        await host.locator('#lobby').waitFor({state:'visible'});
        await guest.goto(base+'/join?pin='+pin);
        assert.ok((await guest.context().cookies()).some(cookie=>cookie.name==='XSRF-TOKEN'), 'A fresh guest needs the form security cookie before joining');
        await Promise.all([guest.waitForURL(url=>url.searchParams.get('lang')==='de'),guest.locator('.live-language select').selectOption('de')]);
        await guest.locator('#nickname').fill('Füchschen');
        await guest.locator('[name=avatar][value=fox]').check();
        await guest.screenshot({path:output+'/join-de.png',fullPage:true,animations:'disabled'});
        await Promise.all([guest.waitForURL('**/play/*'),guest.getByRole('button',{name:'Los geht’s!'}).click()]);
        await guest.getByText('Du bist dabei,',{exact:true}).waitFor();
        await host.waitForFunction(()=>document.getElementById('player-count').textContent==='1');
        await host.screenshot({path:output+'/lobby-de.png',fullPage:true,animations:'disabled'});
        await host.getByRole('button',{name:'Quiz starten →'}).click();
        await guest.waitForFunction(()=>{const el=document.querySelector('.answer-tile[data-value="A"]');return el&&!el.disabled&&el.getBoundingClientRect().height>0;});
        await guest.locator('.answer-tile[data-value=A]').click();
        await guest.waitForFunction(()=>document.getElementById('feedback').textContent.startsWith('Richtig!'));
        await host.getByRole('button',{name:'Siegertreppchen anzeigen →'}).click();
        await guest.getByRole('heading',{name:'Applaus! Ihr habt es euch verdient.'}).waitFor();
        await guest.screenshot({path:output+'/podium-de.png',fullPage:true,animations:'disabled'});
        assert.equal(await guest.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
        assert.deepEqual(errors,[]);
        console.log('PASS: DE selector for guests and hosts, cookie persistence across login/navigation, switching back to EN, quiz creation/editor, German live game and mobile podium; authored content preserved.');
    } catch (error) {
        await host.screenshot({path:output+'/failure.png',fullPage:true,animations:'disabled'});
        console.error('Failed at',host.url(),await host.locator('body').innerText());
        throw error;
    } finally {
        if(quizId) {
            await host.goto(base+'/challenge/create');
            await host.evaluate(async id=>{
                const token=document.querySelector('meta[name=_csrf]').content;
                await fetch('/delete_challenge?challenge='+id,{method:'DELETE',headers:{'X-XSRF-TOKEN':token}});
            },quizId);
        }
        await browser.close();
    }
})().catch(error=>{console.error(error);process.exitCode=1;});
