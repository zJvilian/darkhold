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
