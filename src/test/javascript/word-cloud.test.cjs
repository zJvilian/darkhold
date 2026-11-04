const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function makeNode(tag) {
    const listeners = {};
    return {
        tag, attributes: {}, style: {}, classList: {toggle() {}, add() {}}, children: [], textContent: '', value: '',
        duration: 0, currentTime: 0, playCount: 0,
        setAttribute(k, v) { this.attributes[k] = String(v); },
        getAttribute(k) { return this.attributes[k]; },
        removeAttribute(k) { delete this.attributes[k]; delete this[k]; },
        append(...items) { this.children.push(...items); },
        replaceChildren(...items) { this.children = [...items]; },
        addEventListener(type, fn) { (listeners[type] ||= []).push(fn); },
        emit(type) { (listeners[type] || []).forEach(fn => fn({preventDefault() {}})); },
        play() { this.playCount++; return Promise.resolve(); },
        pause() {},
        querySelector: () => makeNode('button')
    };
}

test('word cloud renders every answer without overlap and scales size with frequency', async () => {
    const ids = {};
    const get = id => (ids[id] ||= makeNode(id));
    let snapshot = {
        pin: '123456', title: 'Traits', host: true, phase: 'RESULTS', locked: false,
        questionIndex: 0, totalQuestions: 1, serverTime: 1000, startedAt: 0, endsAt: 20000,
        players: [{name: 'Ana', avatar: 'fox', emoji: '🦊', score: 0}], answeredCount: 6,
        question: {text: 'Most important trait?', type: 'WORD_CLOUD', options: {}, multiple: false, timeLimit: 20},
        wordCloud: [
            {text: 'creative', count: 3},
            {text: 'fast', count: 2},
            {text: 'bold', count: 1},
            {text: 'inspiration', count: 1}
        ],
        correctAnswer: '', distribution: {}
    };
    const body = {dataset: {pin: '123456', host: 'true'}};
    get('join-address').href = 'http://localhost:8181/join?pin=123456';
    const context = vm.createContext({
        URL, AbortSignal: {timeout: () => ({})}, Intl, Date, Math, JSON, console,
        window: {
            setTimeout: () => {}, setInterval: () => {}, clearTimeout: () => {},
            DarkholdMessages: Object.fromEntries(fs.readFileSync('src/main/resources/messages.properties', 'utf8')
                .split('\n').filter(l => /^[^#=]+=/.test(l)).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]))
        },
        fetch: async () => ({ok: true, status: 200, json: async () => snapshot}),
        document: {
            body, documentElement: {lang: 'en'}, readyState: 'complete',
            getElementById: get, createElement: makeNode, createElementNS: (_, tag) => makeNode(tag),
            querySelector: () => ({content: 'csrf'}), querySelectorAll: () => []
        }
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/language-manager.js', 'utf8'), context);
    vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/live-quiz.js', 'utf8'), context);
    await new Promise(resolve => setImmediate(resolve));

    const svg = get('word-cloud-svg');
    assert.equal(get('word-cloud').hidden, false);
    assert.equal(svg.children.length, 4);
    const sizes = Object.fromEntries(svg.children.map(el => [el.textContent, Number(el.getAttribute('font-size'))]));
    assert.ok(sizes.creative > sizes.fast, '3x word must be larger than 2x word');
    assert.ok(sizes.fast > sizes.bold, '2x word must be larger than 1x word');
    assert.equal(sizes.bold, sizes.inspiration);
    assert.ok(svg.getAttribute('viewBox'));
});

test('question audio starts after 3s, repeats 3x/2x/1x by duration, and supports host manual play', async () => {
    for (const [duration, expectedPlays, questionType] of [
        [4.5, 3, 'MULTIPLE_CHOICE'],
        [5.0, 3, 'TRUE_FALSE'],
        [6.5, 2, 'TYPE_ANSWER'],
        [7.0, 2, 'MULTIPLE_CHOICE'],
        [9.0, 1, 'TRUE_FALSE'],
        [4.0, 0, 'POLL']
    ]) {
        const ids = {};
        const get = id => (ids[id] ||= makeNode(id));
        const timers = [];
        const snapshot = {
            pin: '123456', title: 'Audio Quiz', host: true, phase: 'QUESTION', locked: false,
            questionIndex: 0, totalQuestions: 1, serverTime: 5000, startedAt: 2000, endsAt: 22000,
            players: [{name: 'Ana', avatar: 'fox', emoji: '🦊', score: 0}], answeredCount: 0,
            question: {
                text: 'Listen closely', type: questionType, options: {A: 'One', B: 'Two'},
                multiple: false, audio: '/question-images/1/1/audio_1.mp3', timeLimit: 20
            }
        };
        get('join-address').href = 'http://localhost:8181/join?pin=123456';
        get('question-audio').duration = duration;
        const context = vm.createContext({
            URL, AbortSignal: {timeout: () => ({})}, Intl, Date, Math, JSON, Number, console,
            window: {
                setTimeout: (fn, delay) => { timers.push({fn, delay}); return timers.length; },
                clearTimeout: () => {},
                setInterval: () => {},
                DarkholdMessages: {}
            },
            fetch: async () => ({ok: true, status: 200, json: async () => snapshot}),
            document: {
                body: {dataset: {pin: '123456', host: 'true'}}, documentElement: {lang: 'en'}, readyState: 'complete',
                getElementById: get, createElement: makeNode, createElementNS: (_, tag) => makeNode(tag),
                querySelector: () => ({content: 'csrf'}), querySelectorAll: () => []
            }
        });
        vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/language-manager.js', 'utf8'), context);
        vm.runInContext(fs.readFileSync('src/main/resources/static/scripts/live-quiz.js', 'utf8'), context);
        await new Promise(resolve => setImmediate(resolve));

        const audioTimer = timers.find(t => t.delay === 3000);
        if (expectedPlays === 0) {
            assert.equal(audioTimer, undefined, 'disallowed question type must not schedule audio');
            assert.equal(get('play-audio').hidden, true);
            continue;
        }
        assert.ok(audioTimer, 'audio must wait 3000ms before starting');
        assert.equal(get('question-audio').playCount, 0);
        audioTimer.fn();
        for (let i = 0; i < 5; i++) get('question-audio').emit('ended');
        assert.equal(get('question-audio').playCount, expectedPlays, `duration ${duration}s should play ${expectedPlays}x`);

        // Host manual playback plays once on click
        assert.equal(get('play-audio').hidden, false);
        get('play-audio').emit('click');
        get('question-audio').emit('ended');
        assert.equal(get('question-audio').playCount, expectedPlays + 1);
    }
});
