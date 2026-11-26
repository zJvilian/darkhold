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

function makeNode(tag) {
    const listeners = {};
    return {
        tag, attributes: {}, dataset: {}, style: {}, classList: {toggle() {}, add() {}}, children: [],
        textContent: '', value: '', playCount: 0, currentTime: 0,
        setAttribute(k, v) { this.attributes[k] = String(v); },
        getAttribute(k) { return this.attributes[k]; },
        removeAttribute(k) { delete this.attributes[k]; delete this[k]; },
        append(...items) { this.children.push(...items); },
        replaceChildren(...items) { this.children = [...items]; },
        addEventListener(type, fn) { (listeners[type] ||= []).push(fn); },
        querySelector(selector) { return this.children.find(child => '.' + child.className === selector) || makeNode('span'); },
        play() { this.playCount++; return Promise.resolve(); },
        pause() {}
    };
}

async function loadRoom(snapshot, host) {
    const ids = {};
    const get = id => (ids[id] ||= makeNode(id));
    const timers = [];
    const sounds = [];
    get('join-address').href = 'http://localhost:8181/join?pin=123456';
    const document = {
        body: {dataset: {pin: '123456', host: String(host)}}, documentElement: {lang: 'en'}, readyState: 'complete',
        getElementById: get, createElement: makeNode, createElementNS: (_, tag) => makeNode(tag),
        querySelector: () => ({content: 'csrf'}),
        querySelectorAll: selector => selector === '.answer-tile' ? get('answers').children : []
    };
    const context = vm.createContext({
        URL, AbortSignal: {timeout: () => ({})}, Intl, Date, Math, JSON, Number, console, document,
        AudioManager: {playVictory: () => sounds.push('victory'), playCorrect: () => sounds.push('correct'),
            playIncorrect: () => sounds.push('incorrect'), playJoin: () => sounds.push('join'),
            playCountdown: () => sounds.push('countdown')},
        window: {setTimeout: (fn, delay) => { timers.push({fn, delay}); return timers.length; },
            clearTimeout: () => {}, setInterval: () => {}, KahvoidMessages: {}},
        fetch: async () => ({ok: true, status: 200, json: async () => snapshot})
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/language-manager.js', 'utf8'), context);
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/live-quiz.js', 'utf8'), context);
    await new Promise(resolve => setImmediate(resolve));
    return {get, timers, sounds, document};
}

function room(question, extra = {}) {
    return {
        pin: '123456', title: 'Quiz', host: false, phase: 'QUESTION', locked: false, questionIndex: 0,
        totalQuestions: 1, serverTime: 5000, startedAt: 2000, endsAt: 22000, answeredCount: 0,
        players: [{name: 'Ana', avatar: 'fox', emoji: '🦊', score: 0}],
        question: {text: 'Q', options: {A: 'One', B: 'Two'}, multiple: false, timeLimit: 20, ...question}, ...extra
    };
}

test('live results show the right sorting order to everyone', async () => {
    const options = Object.fromEntries('ABCDEFGHIJ'.split('').map(letter => [letter, 'Item ' + letter]));
    const {get} = await loadRoom(room({type: 'SORTING', options}, {
        phase: 'RESULTS', correctAnswer: 'J,A,B,C,D,E,F,G,H,I', distribution: {},
        feedback: {answered: true, correct: true, earned: 900, streak: 1, poll: false}
    }), false);
    const ranks = Object.fromEntries(get('answers').children.map(tile =>
        [tile.dataset.value, tile.querySelector('.answer-symbol').textContent]));
    assert.equal(ranks.J, 1);
    assert.equal(ranks.A, 2);
    assert.equal(ranks.I, 10);
});

test('live room uses the Kahvoid title and plays the result sound once', async () => {
    const {document, sounds} = await loadRoom(room({type: 'MULTIPLE_CHOICE'}, {
        phase: 'RESULTS', correctAnswer: 'A', distribution: {A: 1, B: 0},
        feedback: {answered: true, correct: false, earned: 0, streak: 0, poll: false}
    }), false);
    assert.equal(document.title, 'Quiz · Kahvoid');
    assert.deepEqual(sounds, ['incorrect']);
});

test('player phones never auto-play question audio', async () => {
    const {get, timers} = await loadRoom(room({type: 'MULTIPLE_CHOICE', audio: '/question-images/1/1/a.mp3'}), false);
    assert.equal(timers.find(timer => timer.delay === 3000), undefined);
    assert.equal(get('question-audio').hidden, true);
    assert.equal(get('play-audio').hidden, true);
});

test('live video accepts every common YouTube link form', async () => {
    for (const link of ['https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=5', 'https://youtu.be/dQw4w9WgXcQ/',
        'https://www.youtube.com/shorts/dQw4w9WgXcQ', 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0']) {
        const {get} = await loadRoom(room({type: 'MULTIPLE_CHOICE', video: link}, {host: true}), true);
        assert.equal(get('question-video').src, 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', link);
    }
    const {get} = await loadRoom(room({type: 'MULTIPLE_CHOICE', video: 'https://evil.example/watch?v=dQw4w9WgXcQ'}), true);
    assert.equal(get('question-video').hidden, true);
});
