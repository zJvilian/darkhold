(() => {
    'use strict';
    const t = LanguageManager.text;
    const number = value => value.toLocaleString(document.documentElement.lang);
    const pin = document.body.dataset.pin;
    const host = document.body.dataset.host === 'true';
    const $ = id => document.getElementById(id);
    const symbols = ['▲', '◆', '●', '■'];
    const audioPattern = /\.(?:mp3|wav|ogg|m4a|aac|flac|webm|opus|oga)(?:$|[?#])/i;
    let state;
    let clockOffset = 0;
    let roundKey = '';
    let playersKey = '';
    let typedQuestionIndex = -1;
    let audioTimer = null;
    let audioQuestionIndex = -1;
    let audioPlaysDone = 0;
    let audioAutoRepeat = false;
    const cloudPalette = ['#5b7cfa', '#f06c64', '#273873', '#4c6ef5', '#8c3b32', '#1c202b', '#2b7a78', '#6741d9'];
    let selected = new Set();
    let submitting = false;
    let fatal = false;
    let requestInFlight = false;

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function show(id, visible) {
        if ($(id)) $(id).hidden = !visible;
    }

    function message(id, text) {
        $(id).textContent = text;
        show(id, !!text);
    }

    async function api(path = '', payload) {
        const headers = {Accept: 'application/json'};
        if (payload) {
            headers['Content-Type'] = 'application/json';
            headers[document.querySelector('meta[name="_csrf_header"]').content] =
                document.querySelector('meta[name="_csrf"]').content;
        }
        const response = await fetch(`/api/live/${pin}${path}`, {
            method: payload ? 'POST' : 'GET', headers, cache: 'no-store',
            body: payload ? JSON.stringify(payload) : undefined,
            signal: AbortSignal.timeout(8000)
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            const error = new Error(data.message || t('live.requestFailed'));
            error.status = response.status;
            throw error;
        }
        return data;
    }

    async function poll() {
        if (fatal) return;
        if (!requestInFlight) {
            requestInFlight = true;
            try {
                render(await api());
                message('connection', '');
            } catch (error) {
                if ([403, 404, 410].includes(error.status)) {
                    fatal = true;
                    message('game-error', error.message + ' ' + t('live.returnToJoin'));
                    document.querySelectorAll('main button').forEach(button => button.disabled = true);
                } else {
                    message('connection', t('live.reconnecting'));
                }
            } finally {
                requestInFlight = false;
            }
        }
        window.setTimeout(poll, 1000);
    }

    function render(data) {
        if (state && data.serverTime < state.serverTime) return;
        const newQuestion = !state || data.questionIndex !== state.questionIndex;
        state = data;
        clockOffset = data.serverTime - Date.now();
        if (newQuestion) selected = new Set();
        if (!['QUESTION', 'RESULTS'].includes(data.phase)) {
            stopAudio();
            audioQuestionIndex = -1;
        }
        show('lobby', data.phase === 'LOBBY');
        show('round', ['QUESTION', 'RESULTS'].includes(data.phase));
        show('leaderboard-section', ['RESULTS', 'FINISHED'].includes(data.phase));
        show('end-game', data.phase !== 'FINISHED');
        document.title = `${data.title} · Darkhold`;
        $('quiz-title').textContent = data.title;
        if (data.me) $('my-score').textContent = `${data.me.emoji} ${data.me.name} · ${t('live.points', number(data.me.score))}`;
        renderLobby();
        renderRound();
        renderScores();
        tick();
    }

    function renderLobby() {
        $('player-count').textContent = state.players.length;
        show('empty-lobby', !state.players.length);
        if (host) {
            $('start-game').disabled = !state.players.length || submitting;
            $('lock-room').textContent = state.locked ? t('live.unlock') : t('live.lock');
        } else if (state.me) {
            $('my-name').textContent = state.me.name;
            $('my-avatar').textContent = state.me.emoji;
        }
        const key = JSON.stringify(state.players);
        if (key === playersKey) return;
        playersKey = key;
        $('players').replaceChildren(...state.players.map(player => {
            const card = element('div', 'player-card');
            card.append(element('span', 'avatar', player.emoji), element('span', 'player-name', player.name));
            if (host) {
                const remove = element('button', 'remove-player', '×');
                remove.type = 'button';
                remove.setAttribute('aria-label', t('live.removePlayer', player.name));
                remove.addEventListener('click', () => control('kick', player.name));
                card.append(remove);
            }
            return card;
        }));
    }

    function renderRound() {
        if (!state.question) return;
        $('question-number').textContent = t('live.questionNumber', state.questionIndex + 1, state.totalQuestions);
        $('answer-count').textContent = t('live.answeredCount', state.answeredCount, state.players.length);
        const key = `${state.questionIndex}:${state.phase}:${state.me?.answered}:${JSON.stringify(state.wordCloud || '')}`;
        if (key === roundKey) return;
        roundKey = key;
        const results = state.phase === 'RESULTS';
        $('question-text').textContent = state.question.text;
        renderMedia();
        const sorting = state.question.type === 'SORTING';
        $('answers').replaceChildren(...Object.entries(state.question.options).map(([value, label], index) => {
            const button = element('button', 'answer-tile');
            button.type = 'button';
            button.dataset.value = value;
            button.append(element('span', 'answer-symbol', symbols[index]), element('span', 'answer-label', optionLabel(value, label)));
            button.addEventListener('click', () => choose(value));
            if (results && !sorting) {
                if (state.correctAnswer) button.classList.add(state.correctAnswer.split(',').includes(value) ? 'correct' : 'incorrect');
                button.append(element('span', 'vote-count', t('live.votes', state.distribution[value] || 0)));
            }
            return button;
        }));
        show('sorting-hint', sorting && !results);
        if (typedQuestionIndex !== state.questionIndex) {
            $('typed-answer').value = '';
            typedQuestionIndex = state.questionIndex;
        }
        renderWordCloud();
        $('answer-hint').textContent = state.question.type === 'WORD_CLOUD'
            ? (host ? t('live.wordCloudHostHint') : (state.me?.answered && state.phase === 'QUESTION' ? t('live.wordCloudSubmittedHint') : t('live.wordCloudHint')))
            : state.question.type === 'POLL' ? t('live.pollHint')
            : state.question.multiple ? t('live.multipleHint') : host ? t('live.hostHint') : t('live.answerHint');
        show('round-result', results);
        show('reveal', host && state.phase === 'QUESTION');
        show('next-question', host && results);
        if (host) $('next-question').textContent = state.questionIndex + 1 === state.totalQuestions ? t('live.showPodium') : t('live.next');
        if (results) renderFeedback();
    }

    function wordColor(text, index) {
        if (index === 0) return cloudPalette[0];
        if (index === 1) return cloudPalette[1];
        let hash = 0;
        for (let i = 0; i < text.length; i++) hash = ((hash << 5) - hash + text.charCodeAt(i)) | 0;
        return cloudPalette[Math.abs(hash) % cloudPalette.length];
    }

    function renderWordCloud() {
        const visible = state.question.type === 'WORD_CLOUD' && Array.isArray(state.wordCloud);
        show('word-cloud', visible);
        if (!visible) return;
        const entries = state.wordCloud;
        const svg = $('word-cloud-svg');
        show('word-cloud-empty', !entries.length);
        show('word-cloud-svg', entries.length > 0);
        if (!entries.length) {
            svg.replaceChildren();
            return;
        }
        const ns = 'http://www.w3.org/2000/svg';
        svg.setAttribute('aria-label', entries.map(e => `${e.text} (${e.count})`).join(', '));
        svg.replaceChildren();
        const maxCount = Math.max(...entries.map(e => e.count));
        const placed = [];
        entries.forEach((entry, index) => {
            const size = Math.round(24 + (maxCount > 1 ? ((entry.count - 1) / Math.max(2, maxCount - 1)) * 56 : 14));
            const text = document.createElementNS(ns, 'text');
            text.textContent = entry.text;
            text.setAttribute('text-anchor', 'middle');
            text.setAttribute('dominant-baseline', 'central');
            text.setAttribute('font-size', String(size));
            text.setAttribute('font-weight', entry.count === maxCount && maxCount > 1 ? '600' : '500');
            text.setAttribute('fill', wordColor(entry.text, index));
            const title = document.createElementNS(ns, 'title');
            title.textContent = `${entry.text} (${entry.count})`;
            text.append(title);
            svg.append(text);
            const bbox = typeof text.getBBox === 'function' ? text.getBBox() : {width: 0, height: 0};
            const w = (bbox.width || size * (entry.text.length * 0.58 + 0.4)) + 16;
            const h = (bbox.height || size * 1.15) + 8;
            let x = 0, y = 0;
            for (let step = 0; step < 2400; step++) {
                const stepAngle = step * 0.35;
                const cx = Math.cos(stepAngle * 1.65) * 5.5 * stepAngle;
                const cy = Math.sin(stepAngle * 1.65) * 3.1 * stepAngle;
                if (!placed.some(b => Math.abs(cx - b.x) * 2 < w + b.w && Math.abs(cy - b.y) * 2 < h + b.h)) {
                    x = cx;
                    y = cy;
                    break;
                }
            }
            text.setAttribute('x', x.toFixed(1));
            text.setAttribute('y', y.toFixed(1));
            placed.push({x, y, w, h});
        });
        const minX = Math.min(...placed.map(b => b.x - b.w / 2));
        const maxX = Math.max(...placed.map(b => b.x + b.w / 2));
        const minY = Math.min(...placed.map(b => b.y - b.h / 2));
        const maxY = Math.max(...placed.map(b => b.y + b.h / 2));
        const vw = Math.max(580, maxX - minX + 48);
        const vh = Math.max(270, maxY - minY + 44);
        const cx = (minX + maxX) / 2;
        const cy = (minY + maxY) / 2;
        svg.setAttribute('viewBox', `${(cx - vw / 2).toFixed(1)} ${(cy - vh / 2).toFixed(1)} ${vw.toFixed(1)} ${vh.toFixed(1)}`);
    }

    function audioRepeatCount(duration) {
        if (!Number.isFinite(duration) || duration <= 0) return 1;
        if (duration <= 5) return 3;
        if (duration <= 7) return 2;
        return 1;
    }

    function stopAudio() {
        if (audioTimer) {
            window.clearTimeout(audioTimer);
            audioTimer = null;
        }
        audioAutoRepeat = false;
        audioPlaysDone = 0;
        const audio = $('question-audio');
        if (audio && typeof audio.pause === 'function') {
            audio.pause();
            audio.currentTime = 0;
        }
    }

    function playAudio(autoRepeat) {
        const audio = $('question-audio');
        if (!audio || !(audio.src || audio.getAttribute?.('src'))) return;
        if (audioTimer) {
            window.clearTimeout(audioTimer);
            audioTimer = null;
        }
        audioAutoRepeat = autoRepeat;
        audioPlaysDone = 0;
        audio.currentTime = 0;
        const result = typeof audio.play === 'function' ? audio.play() : undefined;
        if (result && typeof result.catch === 'function') result.catch(() => {});
    }

    function renderMedia() {
        const image = state.question.image;
        const rawAudio = state.question.audio || (image && audioPattern.test(image) ? image : null);
        const validImage = Boolean(image && !audioPattern.test(image) && /^\/(?:images|question-images|media)\//.test(image));
        show('question-image', validImage);
        if (validImage) $('question-image').src = image;
        else $('question-image')?.removeAttribute('src');
        const audioAllowed = ['MULTIPLE_CHOICE', 'TRUE_FALSE', 'TYPE_ANSWER'].includes(state.question.type);
        const validAudio = Boolean(audioAllowed && rawAudio && /^\/(?:images|question-images|media|sounds)\//.test(rawAudio) && audioPattern.test(rawAudio));
        show('question-audio', validAudio && host);
        show('play-audio', validAudio && host);
        if (validAudio) {
            $('question-audio').src = rawAudio;
            $('question-audio').setAttribute('src', rawAudio);
            if (state.phase === 'QUESTION' && audioQuestionIndex !== state.questionIndex) {
                stopAudio();
                $('question-audio').src = rawAudio;
                $('question-audio').setAttribute('src', rawAudio);
                audioQuestionIndex = state.questionIndex;
                audioTimer = window.setTimeout(() => playAudio(true), 3000);
            } else if (state.phase !== 'QUESTION') {
                stopAudio();
                $('question-audio').src = rawAudio;
                $('question-audio').setAttribute('src', rawAudio);
            }
        } else {
            stopAudio();
            audioQuestionIndex = -1;
            $('question-audio')?.removeAttribute('src');
        }
        let videoId = '';
        try {
            const url = new URL(state.question.video);
            if (['www.youtube.com', 'youtube.com', 'youtu.be'].includes(url.hostname)) {
                videoId = url.hostname === 'youtu.be' ? url.pathname.slice(1) : url.searchParams.get('v') || url.pathname.split('/').pop();
            }
        } catch (_) { /* Questions without video need no player. */ }
        const validVideo = host && /^[\w-]{11}$/.test(videoId);
        show('question-video', validVideo);
        if (validVideo) $('question-video').src = `https://www.youtube-nocookie.com/embed/${videoId}`;
        else $('question-video').removeAttribute('src');
    }

    function optionLabel(value, label) {
        return state.question.type === 'TRUE_FALSE' ? t(value === 'TRUE' ? 'live.true' : 'live.false') : label;
    }

    function renderFeedback() {
        const feedback = state.feedback;
        const isWordCloud = state.question.type === 'WORD_CLOUD';
        $('feedback').textContent = !feedback
            ? (isWordCloud ? t('live.wordCloudResults') : state.question.type === 'POLL' ? t('live.pollResults') : t('live.resultsIn'))
            : feedback.poll ? (feedback.answered ? t('live.pollThanks') : t('live.timeUp'))
            : !feedback.answered ? t('live.timeUp')
            : feedback.correct ? t('live.correctPoints', number(feedback.earned)) + (feedback.streak > 1 ? t('live.streak', feedback.streak) : '')
            : t('live.incorrect');
        if (isWordCloud) {
            const total = (state.wordCloud || []).reduce((sum, item) => sum + item.count, 0);
            $('correct-answer').textContent = t('live.wordCloudTotal', total);
            return;
        }
        const answer = state.question.type === 'TYPE_ANSWER' ? state.correctAnswer
            : (state.correctAnswer || '').split(',').map(value => optionLabel(value, state.question.options[value])).filter(Boolean).join(state.question.type === 'SORTING' ? ' → ' : ' + ');
        $('correct-answer').textContent = answer ? t('live.correctAnswer', answer) : t('live.noCorrectAnswer');
    }

    function renderScores() {
        if (!['RESULTS', 'FINISHED'].includes(state.phase)) return;
        const finished = state.phase === 'FINISHED';
        $('score-title').textContent = finished ? t('live.podiumTitle') : t('live.leaderboard');
        $('score-eyebrow').textContent = finished ? t('live.finalPodium') : t('live.standings');
        show('podium', finished);
        show('play-again', finished);
        $('play-again').href = host ? '/challenge/view' : '/join';
        $('play-again').textContent = host ? t('live.backToQuizzes') : t('live.joinAnother');
        $('leaderboard').replaceChildren(...state.players.map((player, index) => {
            const row = element('li', player.name === state.me?.name ? 'is-me' : '');
            row.append(element('span', 'rank', index + 1), element('span', 'avatar', player.emoji),
                element('strong', 'player-name', player.name), element('span', 'score', t('live.pointsShort', number(player.score))));
            return row;
        }));
        if (finished) $('podium').replaceChildren(...state.players.slice(0, 3).map((player, index) => {
            const place = element('div', 'podium-place');
            place.append(element('span', 'rank', [t('live.first'), t('live.second'), t('live.third')][index]), element('span', 'avatar', player.emoji),
                element('strong', '', player.name), element('span', '', t('live.pointsShort', number(player.score))));
            return place;
        }));
    }

    function tick() {
        if (!state || fatal || !state.question) return;
        const now = Date.now() + clockOffset;
        const countdown = state.phase === 'QUESTION' && now < state.startedAt;
        const answered = !host && state.me?.answered && state.phase === 'QUESTION';
        const active = state.phase === 'QUESTION' && !countdown && now < state.endsAt;
        show('countdown', countdown);
        $('countdown-number').textContent = Math.max(1, Math.ceil((state.startedAt - now) / 1000));
        const isWordCloud = state.question.type === 'WORD_CLOUD';
        show('question-content', !countdown && (!answered || isWordCloud));
        show('answer-sent', answered && !countdown && !isWordCloud);
        const remaining = state.phase === 'QUESTION' ? Math.max(0, Math.ceil((state.endsAt - now) / 1000)) : 0;
        $('timer').textContent = Math.min(state.question.timeLimit, remaining);
        $('timer-bar').style.width = `${Math.min(100, remaining / state.question.timeLimit * 100)}%`;
        document.querySelectorAll('.answer-tile').forEach(button => {
            button.disabled = host || !active || answered || submitting;
            button.classList.toggle('selected', selected.has(button.dataset.value));
            button.setAttribute('aria-pressed', String(selected.has(button.dataset.value)));
            if (state.question.type === 'SORTING') {
                const rank = [...selected].indexOf(button.dataset.value);
                button.querySelector('.answer-symbol').textContent = rank < 0 ? '–' : rank + 1;
            }
        });
        show('typed-answer-form', !host && active && !answered && (state.question.type === 'TYPE_ANSWER' || isWordCloud));
        show('submit-multiple', !host && active && !answered && (state.question.multiple || state.question.type === 'SORTING'));
        $('submit-multiple').textContent = t(state.question.type === 'SORTING' ? 'client.sorting.submit' : 'live.submitMultiple');
        $('submit-multiple').disabled = !selected.size || submitting
            || (state.question.type === 'SORTING' && selected.size !== Object.keys(state.question.options).length);
        $('typed-answer-form').querySelector('button').disabled = submitting;
    }

    function choose(value) {
        if (host || submitting || state.phase !== 'QUESTION') return;
        if (state.question.multiple || state.question.type === 'SORTING') {
            if (selected.has(value)) selected.delete(value); else selected.add(value);
            tick();
        } else {
            selected = new Set([value]);
            submitAnswer(value);
        }
    }

    async function mutate(path, payload) {
        if (submitting || fatal) return;
        submitting = true;
        message('game-error', '');
        tick();
        try {
            render(await api(path, payload));
        } catch (error) {
            message('game-error', error.message);
        } finally {
            submitting = false;
            tick();
        }
    }

    function submitAnswer(answer) {
        return mutate('/answer', {questionIndex: state.questionIndex, answer});
    }

    function control(action, playerName) {
        return mutate('/control', {action, questionIndex: state.questionIndex, playerName});
    }

    $('question-audio')?.addEventListener('ended', () => {
        if (!audioAutoRepeat) return;
        audioPlaysDone++;
        const audio = $('question-audio');
        if (audioPlaysDone < audioRepeatCount(Number(audio.duration))) {
            audio.currentTime = 0;
            const result = typeof audio.play === 'function' ? audio.play() : undefined;
            if (result && typeof result.catch === 'function') result.catch(() => {});
        } else {
            audioAutoRepeat = false;
        }
    });
    $('typed-answer-form').addEventListener('submit', event => {
        event.preventDefault();
        submitAnswer($('typed-answer').value.trim());
    });
    $('submit-multiple').addEventListener('click', () => submitAnswer((state.question.type === 'SORTING' ? [...selected] : [...selected].sort()).join(',')));
    if (host) {
        $('play-audio')?.addEventListener('click', () => playAudio(false));
        $('start-game').addEventListener('click', () => control('start'));
        $('lock-room').addEventListener('click', () => control('lock'));
        $('reveal').addEventListener('click', () => control('reveal'));
        $('next-question').addEventListener('click', () => control('next'));
        $('end-game').addEventListener('click', () => $('end-dialog').showModal());
        $('cancel-end').addEventListener('click', () => $('end-dialog').close());
        $('confirm-end').addEventListener('click', () => { $('end-dialog').close(); control('end'); });
        const joinUrl = $('join-address').href;
        show('local-hint', ['localhost', '127.0.0.1', '[::1]'].includes(new URL(joinUrl).hostname));
        $('copy-link').addEventListener('click', async () => {
            try {
                await navigator.clipboard.writeText(joinUrl);
                $('copy-link').textContent = t('live.copied');
            } catch (_) {
                const range = document.createRange();
                range.selectNodeContents($('join-address'));
                const selection = window.getSelection();
                selection.removeAllRanges();
                selection.addRange(range);
                $('copy-link').textContent = t('live.copySelected');
            }
        });
    }
    window.setInterval(tick, 200);
    poll();
})();
