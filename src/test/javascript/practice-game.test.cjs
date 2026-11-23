const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function game(questionType, mode = 'PRACTICE') {
    const nodes = {};
    function node(id) {
        return nodes[id] ||= {
            id, value: '', style: {}, disabled: false, attributes: {},
            children: [{attributes: {}, setAttribute(k, v) { this.attributes[k] = v; }}],
            classList: {add() {}},
            setAttribute(k, v) { this.attributes[k] = v; }, appendChild() {}, remove() { delete nodes[id]; },
            parentElement: {querySelector: () => ({disabled: false})}
        };
    }
    node('questionType').value = questionType;
    node('gameMode').value = mode;
    node('time_to_flip').value = '20';
    node('correctOptions').value = questionType === 'TRUE_FALSE' ? 'TRUE' : 'A';
    node('practiceNextContainer').style.display = 'none';
    node('nextButton').style.display = 'none';
    node('waitingMessage').style.display = 'none';
    node('typeAnswerInput').value = 'Paris';
    const requests = [];
    const fetches = [];
    let ready;
    let clockStops = 0;
    let timerStops = 0;
    let interval;
    const form = {submit() { this.submitted = true; }};
    function FlipClock() {
        return {stop() { clockStops++; }, getTime: () => ({time: 0}), decrement() {}};
    }
    FlipClock.Timer = function (_, options) {
        interval = options.callbacks.interval;
        return {start() {}, stop() { timerStops++; }};
    };
    const context = vm.createContext({
        console: {log() {}}, Date, URLSearchParams, FlipClock,
        $: () => ({ready(fn) { ready = fn; }, remove() {}}),
        document: {
            getElementById: id => id === 'questionImage' ? null : node(id),
            createElement: id => node(id), querySelectorAll: () => [], forms: [form]
        },
        CsrfManager: {addTokenToXHR() {}, addTokenToFetch: headers => headers},
        LanguageManager: {text: (key, ...args) => [key, ...args].join(':')},
        XMLHttpRequest: function () {
            this.open = (_, url) => { this.url = url; };
            this.setRequestHeader = () => {};
            this.send = body => { this.body = body; requests.push(this); };
        },
        fetch: async (url, options) => {
            const reveal = String(options && options.body) === 'reveal=true';
            fetches.push(reveal ? 'reveal' : url);
            return {ok: true, text: async () => reveal ? context.revealed : 'correct'};
        }
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/game-scripts.js', 'utf8'), context);
    ready();
    return {
        context, node, requests, fetches, form, expire: () => interval(),
        stopped: () => clockStops > 0 && timerStops > 0,
        respond(status = 200, responseText = 'true') {
            const request = requests.at(-1);
            Object.assign(request, {status, responseText});
            request.onload();
        }
    };
}

for (const type of ['MULTIPLE_CHOICE', 'TRUE_FALSE', 'POLL', 'TYPE_ANSWER', 'WORD_CLOUD']) {
    test(`practice ${type} allows advancing after the answer is saved`, () => {
        const g = game(type);
        if (type === 'TYPE_ANSWER' || type === 'WORD_CLOUD') {
            g.context.submitTypeAnswer();
        } else if (type === 'TRUE_FALSE') {
            g.context.waitAndShowTrueFalse(g.node('optionTRUE'));
        } else {
            g.context.waitAndShowAnswer(g.node('optionA'));
        }
        assert.equal(g.node('practiceNextContainer').style.display, 'none', 'wait for the server');
        g.respond(200, type === 'TYPE_ANSWER' ? 'incorrect' : 'correct');
        assert.equal(g.node('practiceNextContainer').style.display, 'block');
        assert.ok(g.stopped(), 'stop both countdown timers');
        assert.equal(g.requests.length, 1, 'do not submit the answer again');
        g.context.callTimeOut();
        assert.equal(g.requests.length, 1, 'late timeout must not submit again');
        g.context.goToNextQuestion();
        assert.equal(g.form.action, '/question');
        assert.equal(g.form.submitted, true);
    });
}

test('practice sorting reveals the solution and allows advancing after validation', async () => {
    const g = game('SORTING');
    await g.context.submitSortingAnswer();
    assert.equal(g.node('practiceNextContainer').style.display, 'block');
    assert.equal(g.node('sortingFeedback').textContent, '✓ live.correctAnswer:A', 'a verdict and labels, not raw letters');
    assert.ok(g.stopped());
    assert.equal(g.requests.length, 0, 'validation already saved the answer');
});

test('a failed answer request does not allow practice to advance early', () => {
    const g = game('MULTIPLE_CHOICE');
    g.context.waitAndShowAnswer(g.node('optionA'));
    g.respond(500);
    assert.equal(g.node('practiceNextContainer').style.display, 'none');
    assert.equal(g.stopped(), false);
});

test('practice still allows advancing when the player runs out of time', () => {
    const g = game('MULTIPLE_CHOICE');
    g.expire();
    assert.equal(g.requests.length, 1);
    g.respond();
    assert.equal(g.node('practiceNextContainer').style.display, 'block');
    assert.ok(g.stopped());
});

test('multiplayer answers keep waiting for the host and the timer', () => {
    const g = game('MULTIPLE_CHOICE', 'MULTIPLAYER');
    g.context.waitAndShowAnswer(g.node('optionA'));
    g.respond();
    assert.equal(g.node('practiceNextContainer').style.display, 'none');
    assert.equal(g.node('waitingMessage').style.display, 'block');
    assert.equal(g.stopped(), false);
});

test('practice keeps a multi-answer question open until every right option is picked', () => {
    const g = game('MULTIPLE_CHOICE');
    g.node('correctOptions').value = 'A,C';
    g.context.waitAndShowAnswer(g.node('optionA'));
    g.context.waitAndShowAnswer(g.node('optionA'));
    assert.equal(g.requests.length, 0, 'one of two right options must not end the question');
    g.context.waitAndShowAnswer(g.node('optionC'));
    assert.equal(g.requests.length, 1);
    assert.equal(g.requests[0].body, 'selectedAnswer=' + encodeURIComponent('A,C'));
    g.respond();
    assert.equal(g.node('practiceNextContainer').style.display, 'block');
});

test('practice ends a multi-answer question on the first wrong option', () => {
    const g = game('MULTIPLE_CHOICE');
    g.node('correctOptions').value = 'A,C';
    g.context.waitAndShowAnswer(g.node('optionA'));
    g.context.waitAndShowAnswer(g.node('optionB'));
    assert.equal(g.requests.length, 1);
    assert.equal(g.requests[0].body, 'selectedAnswer=B');
    assert.equal(g.node('selectedOptions').value, 'incorrect');
});

test('practice multi-answer questions accept correct options written with spaces', () => {
    const g = game('MULTIPLE_CHOICE');
    g.node('correctOptions').value = 'A, C';
    g.context.waitAndShowAnswer(g.node('optionA'));
    assert.equal(g.requests.length, 0);
    g.context.waitAndShowAnswer(g.node('optionC'));
    assert.equal(g.requests[0].body, 'selectedAnswer=' + encodeURIComponent('A,C'));
    assert.equal(g.node('selectedOptions').value, 'correct');
});

test('multiplayer multi-answer questions submit every picked option together', () => {
    const g = game('MULTIPLE_CHOICE', 'MULTIPLAYER');
    g.node('correctOptions').value = '';
    g.node('answerCount').value = '2';
    g.context.waitAndShowAnswer(g.node('optionA'));
    g.context.waitAndShowAnswer(g.node('optionB'));
    g.context.waitAndShowAnswer(g.node('optionB'));
    g.context.waitAndShowAnswer(g.node('optionC'));
    assert.equal(g.requests.length, 0, 'picking options does not answer yet');
    g.context.submitChoices();
    assert.equal(g.requests.length, 1);
    assert.equal(g.requests[0].body, 'selectedAnswer=' + encodeURIComponent('A,C'));
    g.context.waitAndShowAnswer(g.node('optionD'));
    assert.equal(g.requests.length, 1, 'no changes after submitting');
});

test('multiplayer reveals the right answer from the server once the question closes', async () => {
    const g = game('MULTIPLE_CHOICE', 'MULTIPLAYER');
    g.node('correctOptions').value = '';
    g.context.revealed = 'B';
    g.context.waitAndShowAnswer(g.node('optionA'));
    g.respond();
    assert.equal(g.fetches.length, 0, 'the answer stays hidden while the timer runs');
    g.expire();
    g.respond(200, 'false');
    await new Promise(resolve => setImmediate(resolve));
    assert.deepEqual(g.fetches, ['reveal']);
    assert.equal(g.node('correctOptions').value, 'B');
    assert.equal(g.node('selectedOptions').value, 'incorrect');
    assert.equal(g.node('optionB').children[0].attributes.class, 'card text-white bg-success');
    assert.equal(g.node('optionA').children[0].attributes.class, 'card text-white bg-danger');
});

test('the waiting spinner gets real classes and styles', () => {
    const g = game('MULTIPLE_CHOICE', 'MULTIPLAYER');
    g.context.hideOptions();
    assert.equal(g.node('div').className, 'spinner-grow');
    assert.equal(g.node('div').attributes.style, 'width: 3rem; height: 3rem;');
    assert.equal(g.node('optionA').children[0].attributes.style, 'background: #CCC; cursor: default;');
});
