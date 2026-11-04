const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function setup(lang = 'en', href = 'http://localhost:8181/?filter=mine#quiz') {
    const badge = {};
    const context = vm.createContext({
        URL, console,
        window: {location: {href}, DarkholdMessages: {'live.questionNumber': 'Frage {0} von {1}'}},
        localStorage: {getItem: () => 'es'}, navigator: {language: 'es-ES'},
        document: {
            documentElement: {lang}, readyState: 'complete',
            querySelectorAll: () => [badge], getElementById: () => ({value: '42'})
        }
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/language-manager.js', 'utf8'), context);
    return {context, badge, language: vm.runInContext('LanguageManager', context)};
}

test('German selection preserves parameters and updates the server locale through lang=de', () => {
    const {context, language} = setup();
    assert.equal(language.isSupported('de'), true);
    language.changeLanguage('de');
    assert.equal(context.window.location.href, 'http://localhost:8181/?filter=mine&lang=de#quiz');
});

test('badge follows the rendered language instead of stale localStorage or browser preferences', () => {
    const {badge, language} = setup('de');
    assert.equal(badge.textContent, 'DE');
    assert.equal(language.getCurrentLanguage(), 'de');
    assert.equal(language.text('live.questionNumber', 2, 5), 'Frage 2 von 5');
});

test('can switch back to English and ignores unsupported languages', () => {
    const {context, language} = setup('de', 'http://localhost:8181/join?pin=123456&lang=de');
    language.changeLanguage('xx');
    assert.equal(context.window.location.href, 'http://localhost:8181/join?pin=123456&lang=de');
    language.changeLanguage('en');
    assert.equal(context.window.location.href, 'http://localhost:8181/join?pin=123456&lang=en');
});

test('uses a read-only destination for old form pages and preserves preview quiz selection', () => {
    assert.equal(setup('en', 'http://localhost:8181/viewChallenge').language.buildLanguageUrl('de'),
        'http://localhost:8181/challenge/view?lang=de');
    assert.equal(setup('en', 'http://localhost:8181/preconfigure').language.buildLanguageUrl('de'),
        'http://localhost:8181/preconfigure?challenges=42&lang=de');
});
