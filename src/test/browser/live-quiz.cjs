// Run with Playwright available on NODE_PATH and a running local Darkhold instance.
// Uses the seeded demo host unless DARKHOLD_TEST_USERNAME / PASSWORD are set.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { promisify } = require('node:util');
const execFile = promisify(require('node:child_process').execFile);
const base = process.env.DARKHOLD_TEST_URL || 'http://localhost:8181';
const output = process.env.DARKHOLD_TEST_OUTPUT || '/tmp/darkhold-live-smoke';

(async () => {
    fs.mkdirSync(output, { recursive: true });
    const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROMIUM_PATH || undefined });
    const hostContext = await browser.newContext({ serviceWorkers: 'block', viewport: { width: 1440, height: 1050 } });
    const firstContext = await browser.newContext({ serviceWorkers: 'block', viewport: { width: 390, height: 844 } });
    const secondContext = await browser.newContext({ serviceWorkers: 'block', viewport: { width: 430, height: 900 } });
    const host = await hostContext.newPage();
    const first = await firstContext.newPage();
    const second = await secondContext.newPage();
    const pageErrors = [];
    [host, first, second].forEach(page => page.on('pageerror', error => pageErrors.push(error.message)));
    let challengeId;
    let pin;
    const post = async (page, path, payload) => page.evaluate(async ({ path, payload }) => {
        const token = document.querySelector('meta[name="_csrf"]')?.content || document.cookie.split('; ').find(x => x.startsWith('XSRF-TOKEN='))?.split('=')[1];
        const response = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token }, body: JSON.stringify(payload) });
        return { status: response.status, data: await response.json().catch(() => null) };
    }, { path, payload });
    async function waitText(page, selector, text) {
        await page.waitForFunction(({ selector, text }) => document.querySelector(selector)?.textContent.includes(text), { selector, text });
    }
    async function join(page, name, avatar) {
        await page.locator('#nickname').fill(name);
        await page.locator(`input[name=avatar][value=${avatar}]`).check();
        await Promise.all([page.waitForURL('**/play/*'), page.locator('#guest-form button[type=submit]').click()]);
        await waitText(page, '#my-name', name);
    }
    async function answer(page, option) {
        const tile = page.locator(`.answer-tile[data-value="${option}"]`);
        await page.waitForFunction(value => {
            const button = document.querySelector(`.answer-tile[data-value="${value}"]`);
            return button && !button.disabled && button.getBoundingClientRect().height > 0;
        }, option);
        await tile.click();
    }
    async function next() {
        await host.locator('#next-question').waitFor({ state: 'visible' });
        await host.locator('#next-question').click();
    }
    try {
        await host.goto(base + '/login');
        await host.locator('[name=username]').fill(process.env.DARKHOLD_TEST_USERNAME || 'admin@admin.com');
        await host.locator('[name=password]').fill(process.env.DARKHOLD_TEST_PASSWORD || 'admin');
        await Promise.all([host.waitForURL(base + '/'), host.locator('button[type=submit],input[type=submit]').first().click()]);
        await host.goto(base + '/challenge/create');
        challengeId = await host.evaluate(async () => {
            const data = new FormData();
            data.set('title', 'Live smoke ' + Date.now()); data.set('description', 'Temporary browser verification');
            const token = document.cookie.split('; ').find(x => x.startsWith('XSRF-TOKEN=')).split('=')[1];
            const response = await fetch('/save_challenge', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: data });
            return (await response.json()).challengeId;
        });
        assert.ok(challengeId);
        const questions = [
            { question: 'Which planet do we live on?', questionType: 'MULTIPLE_CHOICE', answer1: 'Earth', answer2: 'Mars', answer3: 'Venus', answer4: 'Jupiter', correctOptions: 'A' },
            { question: 'The sky can look blue.', questionType: 'TRUE_FALSE', answer1: 'True', answer2: 'False', correctOptions: 'TRUE' },
            { question: 'Type the name of our planet.', questionType: 'TYPE_ANSWER', answer1: 'Earth', acceptableAnswers: 'Terra' },
            { question: 'Choose the vowels.', questionType: 'MULTIPLE_CHOICE', answer1: 'A', answer2: 'B', answer3: 'E', answer4: 'D', correctOptions: 'A,C' },
            { question: 'Which season is your favorite?', questionType: 'POLL', answer1: 'Summer', answer2: 'Winter', answer3: 'Spring', answer4: 'Autumn', correctOptions: '' }
        ];
        for (const question of questions) assert.equal((await post(host, '/api/question', { ...question, challengeId, points: 1000, timeLimit: 30 })).status, 200);
        await host.goto(base + '/edit_challenge/' + challengeId);
        await Promise.all([host.waitForURL('**/play/*'), host.getByRole('button', { name: 'Host live quiz' }).click()]);
        pin = new URL(host.url()).pathname.split('/').pop();
        fs.writeFileSync(output + '/fixture.json', JSON.stringify({ pin, challengeId }));
        await host.locator('#lobby').waitFor({ state: 'visible' });
        assert.equal(await host.locator('#start-game').isDisabled(), true);
        assert.ok(await host.locator('.qr-card img').evaluate(image => image.naturalWidth > 0));
        // One player uses the home page room code, the other uses a QR-equivalent prefilled link.
        await first.goto(base + '/');
        await first.locator('#gamePin').fill(pin);
        await first.locator('#joinBtn').click();
        await first.waitForURL('**/join?pin=*');
        await first.screenshot({ path: output + '/join-mobile.png', fullPage: true, animations: 'disabled' });
        await join(first, 'Nova', 'fox');
        await second.goto(base + '/join?pin=' + pin);
        await join(second, 'Orbit', 'robot');
        await waitText(host, '#player-count', '2');
        await host.screenshot({ path: output + '/host-lobby.png', fullPage: true, animations: 'disabled' });
        await first.screenshot({ path: output + '/player-lobby.png', fullPage: true, animations: 'disabled' });
        await first.reload();
        await waitText(first, '#my-name', 'Nova');
        assert.equal((await post(first, '/api/live/' + pin + '/control', { action: 'start', questionIndex: -1 })).status, 403);
        // Nickname collisions are readable form errors, never a raw internal JSON error.
        const outsider = await browser.newPage();
        await outsider.goto(base + '/join?pin=' + pin);
        await outsider.locator('#nickname').fill('nova');
        await outsider.locator('#guest-form button[type=submit]').click();
        await waitText(outsider, '[role=alert]', 'taken');
        await outsider.close();
        await host.locator('#start-game').click();
        await answer(first, 'A');
        await first.locator('#answer-sent').waitFor({ state: 'visible' });
        await first.reload();
        await first.locator('#answer-sent').waitFor({ state: 'visible' });
        await answer(second, 'B');
        await host.locator('#round-result').waitFor({ state: 'visible' });
        await waitText(first, '#feedback', 'Correct!');
        await host.screenshot({ path: output + '/results.png', fullPage: true, animations: 'disabled' });
        await next();
        await answer(first, 'TRUE');
        await answer(second, 'FALSE');
        await next();
        await first.locator('#typed-answer-form').waitFor({ state: 'visible' });
        await first.locator('#typed-answer').fill('Terra');
        await first.locator('#typed-answer-form button').click();
        await second.locator('#typed-answer-form').waitFor({ state: 'visible' });
        await second.locator('#typed-answer').fill('Earth');
        await second.locator('#typed-answer-form button').click();
        await next();
        for (const player of [first, second]) {
            await answer(player, 'A'); await answer(player, 'C');
            await player.locator('#submit-multiple').click();
        }
        await next();
        await answer(first, 'B'); await answer(second, 'D');
        await waitText(first, '#feedback', 'opinion');
        await next();
        await host.locator('#podium').waitFor({ state: 'visible' });
        await first.locator('#podium').waitFor({ state: 'visible' });
        assert.equal(await host.locator('.podium-place').count(), 2);
        assert.match(await host.locator('.podium-place').first().innerText(), /Nova/);
        await host.screenshot({ path: output + '/podium.png', fullPage: true, animations: 'disabled' });
        await first.screenshot({ path: output + '/podium-mobile.png', fullPage: true, animations: 'disabled' });
        for (const page of [first, second]) {
            assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, 'Mobile screen must not overflow');
        }
        await host.goto(base + '/my-active-games');
        await host.locator(`a[href="/play/${pin}"]`).waitFor();
        assert.deepEqual(pageErrors, [], 'Browser scripts should not throw');
        if (process.env.DARKHOLD_TEST_RESTART === '1') {
            await execFile('docker', ['compose', 'restart', 'darkhold']);
            for (let attempt = 0; attempt < 45; attempt++) {
                if (await fetch(base + '/actuator/health').then(response => response.ok).catch(() => false)) break;
                await new Promise(resolve => setTimeout(resolve, 1000));
            }
            for (const player of [first, second]) {
                await player.reload();
                await player.locator('#podium').waitFor({ state: 'visible' });
                assert.equal(await player.locator('.podium-place').count(), 2);
            }
            console.log('PASS: persisted room, scores and both guest identities survive a Docker restart.');
            // The host uses normal authentication and signs in again after restart.
            await host.goto(base + '/login');
            await host.locator('[name=username]').fill(process.env.DARKHOLD_TEST_USERNAME || 'admin@admin.com');
            await host.locator('[name=password]').fill(process.env.DARKHOLD_TEST_PASSWORD || 'admin');
            await Promise.all([host.waitForURL(base + '/'), host.locator('button[type=submit],input[type=submit]').first().click()]);
        }
        console.log('PASS: host + two account-free players; code/link joins; avatars; duplicate names; refresh; host authorization; all four question types; scores; podium; mobile layout.');
        console.log('Fixture and screenshots:', output);
    } finally {
        if (challengeId) {
            await host.goto(base + '/challenge/create');
            await host.evaluate(async id => {
                const token = document.cookie.split('; ').find(x => x.startsWith('XSRF-TOKEN=')).split('=')[1];
                await fetch('/delete_challenge?challenge=' + id, { method: 'DELETE', headers: { 'X-XSRF-TOKEN': token } });
            }, challengeId);
        }
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
