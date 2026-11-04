const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

test('live sorting preserves selection order and allows corrections before submitting', () => {
    const source = fs.readFileSync('src/main/resources/static/scripts/live-quiz.js', 'utf8');
    const choose = source.match(/    function choose\(value\) \{[\s\S]*?\n    \}/)[0];
    const submit = source.split('\n').find(line => line.includes("$('submit-multiple').addEventListener('click'"));
    let handler;
    let submitted;
    const context = vm.createContext({
        host: false, submitting: false, state: {phase: 'QUESTION', question: {type: 'SORTING'}},
        selected: new Set(), tick() {}, submitAnswer(value) { submitted = value; },
        $() { return {addEventListener(_, callback) { handler = callback; }}; }
    });
    vm.runInContext(choose + '\n' + submit, context);
    ['C', 'B', 'B', 'A', 'B'].forEach(value => context.choose(value));
    assert.equal(submitted, undefined);
    handler();
    assert.equal(submitted, 'C,A,B');
});
