const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function setup(response) {
    const element = () => ({
        style: {}, children: [],
        appendChild(child) { this.children.push(child); },
        replaceChildren(...children) { this.children = children; }
    });
    const elements = {progress: element(), progressdiv: element()};
    const context = vm.createContext({
        FormData, console,
        window: {DarkholdMessages: Object.fromEntries(fs.readFileSync('src/main/resources/messages.properties', 'utf8').split('\n').filter(line => /^[^#=]+=/.test(line)).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]))},
        document: {
            cookie: 'XSRF-TOKEN=test-token', readyState: 'loading',
            addEventListener() {}, createElement: element,
            getElementById: id => elements[id]
        },
        fetch: async (url, options) => {
            assert.equal(url, '/save_challenge');
            assert.equal(options.headers['X-XSRF-TOKEN'], 'test-token');
            assert.equal(options.body.get('title'), 'Quiz');
            return response;
        },
        XMLHttpRequest: class {
            constructor() { context.xhr = this; this.headers = {}; this.upload = {}; }
            open(method, url) { assert.equal(method, 'POST'); assert.equal(url, '/upload_challenge'); }
            setRequestHeader(name, value) { this.headers[name] = value; }
            send() {}
        }
    });
    for (const name of ['language-manager', 'csrf-manager', 'create-challenge-scripts']) {
        vm.runInContext(fs.readFileSync(`src/main/resources/static/scripts/${name}.js`, 'utf8'), context);
    }
    return {context, button: element(), result: element()};
}

for (const scenario of [
    {name: 'creates a quiz with CSRF', status: 200, body: {challengeId: 42, message: '<b>Quiz</b>'}, success: true},
    {name: 'shows JSON validation errors', status: 400, body: {message: 'Title is required'}, message: 'Title is required'},
    {name: 'handles expired sessions', status: 200, html: true, redirected: true, message: /sign in again/},
    {name: 'handles forbidden HTML responses', status: 403, html: true, message: /sign in again/},
    {name: 'handles server HTML errors', status: 500, html: true, message: /Unable to create/},
    {name: 'handles malformed JSON', status: 200, malformed: true, message: /Unable to create/}
]) {
    test(scenario.name, async () => {
        const {context, button, result} = setup({
            status: scenario.status, redirected: scenario.redirected,
            headers: new Headers({'content-type': scenario.html ? 'text/html' : 'application/json'}),
            json: async () => {
                assert.ok(!scenario.html, 'HTML must never be parsed as JSON');
                if (scenario.malformed) throw new SyntaxError('Invalid JSON');
                return scenario.body;
            }
        });
        await context.createEmptyChallenge(button, 'Quiz', '', result);
        if (scenario.success) {
            assert.equal(button.style.display, 'none');
            assert.equal(result.children[1].href, '/edit_challenge/42');
            assert.equal(result.children[0].textContent, 'Quiz created successfully.');
            assert.equal(result.children[0].innerHTML, undefined);
        } else {
            assert.equal(button.disabled, false);
            assert.match(result.children[0].textContent, new RegExp(scenario.message));
        }
    });
}

test('Excel upload sends CSRF and handles success and invalid responses', () => {
    const {context, button, result} = setup();
    context.uploadWithExcel(button, new Blob(['xlsx']), 'Quiz', '', result);
    const xhr = context.xhr;
    assert.equal(xhr.headers['X-XSRF-TOKEN'], 'test-token');
    assert.equal(xhr.responseType, 'json');
    xhr.status = 403;
    xhr.response = null;
    xhr.onload();
    assert.equal(button.disabled, false);
    assert.match(result.children[0].textContent, /sign in again/);
    xhr.status = 200;
    xhr.response = {challengeId: 7, message: 'Created'};
    context.viewChallenge = (_, id) => assert.equal(id, 7);
    xhr.onload();
    assert.equal(button.style.display, 'none');
});
