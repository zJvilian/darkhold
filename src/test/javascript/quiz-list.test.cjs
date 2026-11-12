const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function page(rowSpecs) {
    const control = () => ({value: '', addEventListener() {}});
    const select = {value: '', options: [], add(option) { this.options.push(option); }, addEventListener() {}};
    const nodes = {
        quizSearch: control(), quizSort: {value: 'nameAsc', addEventListener() {}}, quizOwner: select,
        quizOwnerField: {hidden: true}, quizCount: {}, quizNoMatches: {}
    };
    const rows = rowSpecs.map(([title, owner, owned]) => ({
        dataset: {title, owner, owned: String(owned), created: '', questions: '1'}, hidden: false
    }));
    nodes.quizRows = {querySelectorAll: selector => (selector === '.quiz-row' ? rows : []), appendChild() {}};
    const context = vm.createContext({
        document: {readyState: 'complete', getElementById: id => nodes[id], addEventListener() {}},
        LanguageManager: {text: () => ''}, Option: function (text, value) { this.text = text; this.value = value; }
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/view-challenges-scripts.js', 'utf8'), context);
    return {select, rows, field: nodes.quizOwnerField};
}

test('a manager starts on their own quizzes and can switch to all owners', () => {
    const {select, rows, field} = page([['Mine', 'Julian Ilg', true], ['Theirs', 'Test Test', false]]);
    assert.equal(field.hidden, false);
    assert.equal(select.value, 'Julian Ilg');
    assert.deepEqual(rows.map(r => r.hidden), [false, true]);
});

test('without own quizzes the list starts on all owners', () => {
    const {select, rows} = page([['A', 'Test Test', false], ['B', 'Other One', false]]);
    assert.equal(select.value, '');
    assert.deepEqual(rows.map(r => r.hidden), [false, false]);
});
