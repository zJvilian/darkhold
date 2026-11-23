const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function setup() {
    const button = {disabled: false};
    const form = {
        elements: {namedItem: name => ({value: name === 'email' ? 'someone+test@example.invalid' : '8'})},
        querySelector: () => button,
        submit() { this.submitted = true; }
    };
    const context = vm.createContext({
        URLSearchParams, console,
        document: {
            cookie: 'XSRF-TOKEN=test-token', readyState: 'loading', addEventListener() {},
            getElementById: () => ({textContent: 'Warning'})
        },
        LanguageManager: {text: key => key},
        showModelDialog: (title, message) => { context.message = message; },
        XMLHttpRequest: class {
            constructor() { context.xhr = this; this.headers = {}; this.responseURL = ''; }
            open(method, url) { assert.equal(method, 'POST'); assert.equal(url, '/users/check_email'); }
            setRequestHeader(name, value) { this.headers[name] = value; }
            send(body) {
                assert.equal(new URLSearchParams(body).get('email'), 'someone+test@example.invalid');
                assert.equal(new URLSearchParams(body).get('id'), '8');
                assert.equal(typeof this.onload, 'function');
            }
        }
    });
    for (const name of ['csrf-manager', 'user-management-scripts']) {
        vm.runInContext(fs.readFileSync(`src/main/resources/static/scripts/${name}.js`, 'utf8'), context);
    }
    assert.equal(context.checkEmailUnique(form), false);
    assert.equal(context.xhr.headers['X-XSRF-TOKEN'], 'test-token');
    assert.equal(button.disabled, true);
    return {context, form, button};
}

test('successful CSRF-protected email check submits the user form', () => {
    const {context, form} = setup();
    Object.assign(context.xhr, {status: 200, responseText: 'OK'});
    context.xhr.onload();
    assert.equal(form.submitted, true);
});

for (const [status, responseText, responseURL, key] of [
    [200, 'DUPLICATED', '', 'client.user.emailDuplicate'],
    [403, '<html>Forbidden</html>', '', 'client.request.forbidden'],
    [200, '<html>Sign in</html>', 'http://localhost/login', 'client.request.login'],
    [401, '', '', 'client.request.login'],
    [500, '', '', 'client.request.failed'],
    [200, 'unexpected', '', 'client.request.failed']
]) {
    test(`email-check failure ${status} ${key} gives feedback and allows retry`, () => {
        const {context, form, button} = setup();
        Object.assign(context.xhr, {status, responseText, responseURL});
        context.xhr.onload();
        assert.equal(context.message, key);
        assert.equal(button.disabled, false);
        assert.ok(!form.submitted);
    });
}
for (const event of ['onerror', 'ontimeout']) {
    test(`${event} gives feedback and allows retry`, () => {
        const {context, form, button} = setup();
        assert.equal(context.xhr.timeout, 15000);
        context.xhr[event]();
        assert.equal(context.message, 'client.request.network');
        assert.equal(button.disabled, false);
        assert.ok(!form.submitted);
    });
}

test('CsrfManager reads fresh cookie, uses X-XSRF-TOKEN for STOMP, and omits null hidden input', () => {
    const context = vm.createContext({
        console,
        document: {
            cookie: 'XSRF-TOKEN=token-1',
            readyState: 'loading',
            addEventListener() {},
            createElement: tag => ({tag}),
            querySelectorAll: () => []
        }
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/csrf-manager.js', 'utf8'), context);
    const csrf = vm.runInContext('CsrfManager', context);
    assert.deepEqual({...csrf.getHeadersForStomp()}, {'X-XSRF-TOKEN': 'token-1'});
    context.document.cookie = 'XSRF-TOKEN=token-2';
    assert.equal(csrf.getToken(), 'token-2');
    context.document.cookie = '';
    assert.equal(csrf.getToken(), null);
    assert.equal(csrf.createHiddenInput(), null);
});

test('ThemeManager preserves unset localStorage on init, watches system preference, and restores colorblind mode', () => {
    const store = new Map([['darkhold_colorblind', 'deuteranopia']]);
    const attrs = new Map();
    let mediaListener = null;
    const context = vm.createContext({
        console,
        CustomEvent: class { constructor(type, init) { this.type = type; this.detail = init?.detail; } },
        localStorage: {
            getItem: k => store.has(k) ? store.get(k) : null,
            setItem: (k, v) => store.set(k, v)
        },
        window: {
            matchMedia: () => ({
                matches: true,
                addEventListener: (ev, fn) => { mediaListener = fn; }
            }),
            dispatchEvent() {}
        },
        document: {
            readyState: 'complete',
            documentElement: {
                setAttribute: (k, v) => attrs.set(k, v),
                removeAttribute: k => attrs.delete(k)
            }
        }
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/theme-manager.js', 'utf8'), context);
    const tm = vm.runInContext('ThemeManager', context);
    assert.equal(attrs.get('data-theme'), 'dark');
    assert.equal(store.has('darkhold_theme'), false);
    assert.equal(attrs.get('data-colorblind'), 'deuteranopia');
    assert.equal(typeof mediaListener, 'function');
    mediaListener({matches: false});
    assert.equal(attrs.get('data-theme'), 'light');
    tm.toggle();
    assert.equal(store.get('darkhold_theme'), 'dark');
    tm.setColorblindMode('none');
    assert.equal(attrs.has('data-colorblind'), false);
});

test('A11y sets up skip link and keyboard navigation immediately without nested DOMContentLoaded', () => {
    const inserted = [];
    const clickableAttrs = new Map();
    const clickable = {
        hasAttribute: k => clickableAttrs.has(k),
        setAttribute: (k, v) => clickableAttrs.set(k, v),
        addEventListener() {}
    };
    const context = vm.createContext({
        console, setTimeout,
        document: {
            readyState: 'complete',
            body: {
                firstChild: null,
                appendChild() {},
                insertBefore: node => inserted.push(node)
            },
            createElement: tag => ({tag, style: {}, setAttribute() {}, addEventListener() {}}),
            getElementById: () => null,
            addEventListener(ev) {
                if (ev === 'DOMContentLoaded') {
                    throw new Error('Must not register nested DOMContentLoaded');
                }
            },
            querySelectorAll: sel => sel.includes('[onclick]') ? [clickable] : []
        }
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/accessibility.js', 'utf8'), context);
    assert.equal(inserted.length, 1);
    assert.equal(inserted[0].id, 'skip-to-main');
    assert.equal(clickableAttrs.get('tabindex'), '0');
});

test('logOut submits dedicated darkholdLogoutForm instead of hijacking document.forms[0]', () => {
    let otherFormSubmitted = false;
    let logoutFormSubmitted = false;
    const context = vm.createContext({
        console,
        navigator: {},
        window: {location: {href: '/account', search: ''}, addEventListener() {}},
        document: {
            readyState: 'loading',
            documentElement: {lang: 'en'},
            forms: [{action: '/other', submit() { otherFormSubmitted = true; }}],
            addEventListener() {},
            getElementById: id => id === 'darkholdLogoutForm' ? {submit() { logoutFormSubmitted = true; }} : null
        }
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/common-scripts.js', 'utf8'), context);
    context.logOut();
    assert.equal(logoutFormSubmitted, true);
    assert.equal(otherFormSubmitted, false);
    context.logMeIn();
    assert.equal(context.window.location.href, '/login');
});
