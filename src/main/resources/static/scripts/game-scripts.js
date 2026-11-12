let clock2;
let timer;
let questionClosed = false;

$(document).ready(function () {
    let time_to_flip = document.getElementById('time_to_flip').value;
    clock2 = new FlipClock($('.clock2'), time_to_flip, {
        clockFace: 'Counter'
    });

    timer = new FlipClock.Timer(clock2, {
        callbacks: {
            interval: function () {
                let timeRemaining = clock2.getTime().time;

                if (timeRemaining <= 0) {
                    clock2.stop();
                    timer.stop();
                    callTimeOut();
                } else {
                    // Play countdown warning at 5 seconds
                    if (timeRemaining === 5 && typeof AudioManager !== 'undefined') {
                        AudioManager.playCountdown();
                    }
                    // Play tick sound every second (optional, can be disabled if too annoying)
                    // if (typeof AudioManager !== 'undefined') {
                    //     AudioManager.playTick();
                    // }
                }
                clock2.decrement()
            }
        }
    });
    timer.start();

    $("span.flip-clock-divider").remove();

    // Setup keyboard navigation for answer options
    if (typeof A11y !== 'undefined') {
        A11y.setupAnswerNavigation('#answerSpace');
    }

    initQuestionAudio();
});

let questionAudioTimer = null;
let questionAudioPlaysDone = 0;
let questionAudioAutoRepeat = false;

function questionAudioRepeats(duration) {
    if (!Number.isFinite(duration) || duration <= 0) return 1;
    if (duration <= 5) return 3;
    if (duration <= 7) return 2;
    return 1;
}

function initQuestionAudio() {
    const img = document.getElementById('questionImage');
    const audio = document.getElementById('questionAudio');
    if (!img || !audio) return;
    const src = img.getAttribute('src') || '';
    if (!/\.(?:mp3|wav|ogg|m4a|aac|flac|webm|opus|oga)(?:$|[?#])/i.test(src)) return;
    img.style.display = 'none';
    img.removeAttribute('src');
    if (!['MULTIPLE_CHOICE', 'TRUE_FALSE', 'TYPE_ANSWER'].includes(getQuestionType())) return;
    audio.src = src;
    const playBtn = document.getElementById('playAudioButton');
    if (playBtn) playBtn.style.display = 'inline-flex';
    audio.addEventListener('ended', function () {
        if (!questionAudioAutoRepeat) return;
        questionAudioPlaysDone++;
        if (questionAudioPlaysDone < questionAudioRepeats(Number(audio.duration))) {
            audio.currentTime = 0;
            const res = audio.play();
            if (res && typeof res.catch === 'function') res.catch(function () {});
        } else {
            questionAudioAutoRepeat = false;
        }
    });
    questionAudioTimer = setTimeout(function () {
        startQuestionAudio(true);
    }, 3000);
}

function startQuestionAudio(autoRepeat) {
    const audio = document.getElementById('questionAudio');
    if (!audio || !audio.src) return;
    if (questionAudioTimer) {
        clearTimeout(questionAudioTimer);
        questionAudioTimer = null;
    }
    questionAudioAutoRepeat = autoRepeat;
    questionAudioPlaysDone = 0;
    audio.currentTime = 0;
    const res = audio.play();
    if (res && typeof res.catch === 'function') res.catch(function () {});
}

function playQuestionAudioManually() {
    startQuestionAudio(false);
}

function callTimeOut(answerSaved = false) {
    if (questionClosed) return;
    questionClosed = true;
    if (clock2) clock2.stop();
    if (timer) timer.stop();
    if (questionAudioTimer) {
        clearTimeout(questionAudioTimer);
        questionAudioTimer = null;
    }
    questionAudioAutoRepeat = false;
    isAnswerSelected = true;
    let questionType = getQuestionType();

    // For TYPE_ANSWER, if no answer submitted, mark as timeout
    if (questionType === 'TYPE_ANSWER' || questionType === 'WORD_CLOUD') {
        let inputElem = document.getElementById('typeAnswerInput');
        if (inputElem && !inputElem.disabled) {
            document.getElementById('selectedOptions').value = "timeout";
            inputElem.disabled = true;
            let btn = inputElem.parentElement.querySelector('button');
            if (btn) btn.disabled = true;
        }
    }

    if (!answerSaved && (questionType !== 'SORTING' || !sortingSubmitted)) saveAnswerOnAjax();
    if (questionType === 'SORTING') {
        sortingClosed = true;
        document.querySelectorAll('#sortingAnswers button').forEach(button => button.disabled = true);
        document.getElementById('sortingFeedback').textContent = document.getElementById('correctOptions').value;
    }

    // Check if practice mode
    let gameMode = document.getElementById('gameMode') ? document.getElementById('gameMode').value : 'MULTIPLAYER';
    if (gameMode === 'PRACTICE') {
        // Show practice mode next button
        let practiceNextContainer = document.getElementById('practiceNextContainer');
        if (practiceNextContainer) {
            practiceNextContainer.style.display = 'block';
        }
    } else {
        // Show moderator next button
        let nextButton = document.getElementById('nextButton');
        if (nextButton) {
            nextButton.setAttribute('style', 'display:block');
        }
    }

    // Only highlight correct/incorrect for non-POLL questions
    if (questionType !== 'POLL' && questionType !== 'WORD_CLOUD' && questionType !== 'SORTING') {
        highlightGreen();
        highlightRedOnIncorrectSelection();
    }
    const spinner = document.getElementById('spinner');
    if (spinner) spinner.remove();
}

function finishPracticeAnswer() {
    const gameMode = document.getElementById('gameMode');
    if (gameMode && gameMode.value === 'PRACTICE') callTimeOut(true);
}

function highlightGreen() {
    let questionType = getQuestionType();
    let correctAnswer = document.getElementById('correctOptions').value;

    if (questionType === 'TRUE_FALSE') {
        let correctDiv = "option" + correctAnswer;
        let toGreenChild = document.getElementById(correctDiv);
        if (toGreenChild) {
            toGreenChild.children[0].setAttribute('class', 'card text-white bg-success');
            // Add animation class
            toGreenChild.classList.add('answer-correct');
        }
    } else if (questionType === 'TYPE_ANSWER' || questionType === 'WORD_CLOUD') {
        // For TYPE_ANSWER, feedback is shown inline via input styling
        return;
    } else {
        // MULTIPLE_CHOICE
        let correctDiv = "option" + correctAnswer;
        let toGreenChild = document.getElementById(correctDiv);
        if (toGreenChild) {
            toGreenChild.children[0].setAttribute('class', 'card text-white bg-success');
            // Add animation class
            toGreenChild.classList.add('answer-correct');
        }
    }

    // Play correct sound only if user selected the correct answer
    let selectedOptions = document.getElementById('selectedOptions').value;
    if (selectedOptions === "correct" && typeof AudioManager !== 'undefined') {
        AudioManager.playCorrect();
    }

    // Announce correct answer for screen readers
    if (typeof A11y !== 'undefined') {
        if (selectedOptions === "correct") {
            A11y.announce('Correct! The answer was Option ' + correctAnswer);
        } else {
            A11y.announce('The correct answer has been revealed: Option ' + correctAnswer);
        }
    }
}

function highlightRedOnIncorrectSelection() {
    let questionType = getQuestionType();
    let selectedOptions = document.getElementById('selectedOptions').value;

    if (selectedOptions === "correct" || selectedOptions === "poll") {
        return;
    }

    let selectedAnswer = document.getElementById('selectedAnswer').value;
    if (!selectedAnswer) return;

    if (questionType === 'TRUE_FALSE') {
        let inCorrectDiv = "option" + selectedAnswer;
        let toRedChild = document.getElementById(inCorrectDiv);
        if (toRedChild) {
            toRedChild.children[0].setAttribute('class', 'card text-white bg-danger');
            // Add animation class
            toRedChild.classList.add('answer-incorrect');
        }
    } else if (questionType === 'TYPE_ANSWER' || questionType === 'WORD_CLOUD') {
        // Already handled in validateTypeAnswerOnServer
        return;
    } else {
        // MULTIPLE_CHOICE
        let inCorrectDiv = "option" + selectedAnswer;
        let toRedChild = document.getElementById(inCorrectDiv);
        if (toRedChild) {
            toRedChild.children[0].setAttribute('class', 'card text-white bg-danger');
            // Add animation class
            toRedChild.classList.add('answer-incorrect');
        }
    }

    // Play incorrect sound and announce
    if (typeof AudioManager !== 'undefined') {
        AudioManager.playIncorrect();
    }
    if (typeof A11y !== 'undefined') {
        A11y.announce('Incorrect answer selected');
    }
}

let isAnswerSelected = false;
let startTime;
let endTime;

function getQuestionType() {
    let typeElem = document.getElementById('questionType');
    return typeElem ? typeElem.value : 'MULTIPLE_CHOICE';
}

function waitAndShowAnswer(elem) {
    let roles = document.getElementById("roles").value;
    console.log("roles is " + roles);
    if (!roles.includes("ROLE_MODERATOR")) {
        if (!isAnswerSelected) {
            realWaitAndShowAnswer(elem);
            if (!isAnswerSelected) {
                isAnswerSelected = true;
            }
            // Submit now: the server times the answer, so it must not wait for the timer to run out.
            saveAnswerOnAjax();
        } else {
            console.log('answer already selected !!!');
        }
    } else {
        console.log('This is ROLE_MODERATOR who cannot play');
    }
}

function realWaitAndShowAnswer(elem) {
    let questionType = getQuestionType();
    let correctAnswer = document.getElementById('correctOptions').value;
    console.log('correctAnswer ' + correctAnswer);
    elem.children[0].setAttribute('class', 'card text-white bg-dark');
    let selectedAnswer = elem.id.charAt(elem.id.length - 1);
    console.log('selectedAnswer ' + selectedAnswer);
    document.getElementById('selectedAnswer').value = selectedAnswer;

    // For POLL questions, there's no correct answer
    if (questionType === 'POLL') {
        document.getElementById('selectedOptions').value = "poll";
        hideOptions();
        endTime = new Date().getTime();
        return;
    }

    // split with comma
    let answers = correctAnswer.split(",");
    let correct = false;
    for (let i = 0; i < answers.length; i++) {
        if (selectedAnswer === answers[i]) {
            correct = true;
        }
    }
    if (correct) {
        document.getElementById('selectedOptions').value = "correct";
        hideOptions();

        // Announce submission (sound will be played later when timer expires)
        if (typeof A11y !== 'undefined') {
            A11y.announce('Answer submitted.');
        }
    } else {
        document.getElementById('selectedOptions').value = "incorrect";
        hideOptions();

        // Sounds and announcements will be played later in highlightRedOnIncorrectSelection
    }
    endTime = new Date().getTime();
    let timeTookForFirstClick = endTime - startTime;
    console.log('Execution time: ' + timeTookForFirstClick);
}

// Handler for TRUE_FALSE questions
function waitAndShowTrueFalse(elem) {
    let roles = document.getElementById("roles").value;
    if (roles.includes("ROLE_MODERATOR")) {
        console.log('This is ROLE_MODERATOR who cannot play');
        return;
    }
    if (isAnswerSelected) {
        console.log('answer already selected !!!');
        return;
    }
    isAnswerSelected = true;

    let correctAnswer = document.getElementById('correctOptions').value;
    let selectedAnswer = elem.id.replace('option', ''); // 'TRUE' or 'FALSE'
    console.log('TRUE_FALSE: selected=' + selectedAnswer + ', correct=' + correctAnswer);

    document.getElementById('selectedAnswer').value = selectedAnswer;
    elem.children[0].setAttribute('class', 'card text-white bg-dark');

    let correct = selectedAnswer === correctAnswer;
    document.getElementById('selectedOptions').value = correct ? "correct" : "incorrect";

    hideTrueFalseOptions();
    endTime = new Date().getTime();
    saveAnswerOnAjax();
}

function hideTrueFalseOptions() {
    ['optionTRUE', 'optionFALSE'].forEach(function(id) {
        let opt = document.getElementById(id);
        if (opt) {
            opt.setAttribute('style', 'opacity: 0.7; cursor: default;');
        }
    });
}

// Handler for TYPE_ANSWER questions
function submitTypeAnswer() {
    let roles = document.getElementById("roles").value;
    if (roles.includes("ROLE_MODERATOR")) {
        console.log('This is ROLE_MODERATOR who cannot play');
        return;
    }
    if (isAnswerSelected) {
        console.log('answer already selected !!!');
        return;
    }

    let inputElem = document.getElementById('typeAnswerInput');
    let userAnswer = inputElem ? inputElem.value.trim() : '';
    if (!userAnswer) {
        alert('Please enter an answer');
        return;
    }

    isAnswerSelected = true;
    endTime = new Date().getTime();

    // Disable input and button
    inputElem.disabled = true;
    let btn = inputElem.parentElement.querySelector('button');
    if (btn) btn.disabled = true;

    // Send to server for fuzzy matching validation
    validateTypeAnswerOnServer(userAnswer);
}

function validateTypeAnswerOnServer(userAnswer) {
    let xhr = new XMLHttpRequest();
    xhr.open('POST', "/validate_answer/");
    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
    CsrfManager.addTokenToXHR(xhr);
    xhr.onload = function () {
        if (xhr.status === 200) {
            let result = xhr.responseText; // 'correct' or 'incorrect'
            console.log('TYPE_ANSWER validation result: ' + result);
            document.getElementById('selectedOptions').value = result;

            // Show feedback
            let inputElem = document.getElementById('typeAnswerInput');
            if (result === 'correct') {
                inputElem.classList.add('bg-success', 'text-white');
            } else {
                inputElem.classList.add('bg-danger', 'text-white');
            }
            finishPracticeAnswer();
        } else {
            console.log('Validation request failed: ' + xhr.status);
            document.getElementById('selectedOptions').value = "incorrect";
        }
    };
    xhr.send('userAnswer=' + encodeURIComponent(userAnswer));
}

function hideOptions() {
    let optionA = document.getElementById("optionA");
    //optionA.setAttribute('style','display:none');
    optionA.setAttribute('style', 'opacity: 0.7');
    optionA.children[0].setAttribute('style', 'background: #CCC');
    optionA.children[0].setAttribute('style', 'cursor:default');
    let optionB = document.getElementById("optionB");
    //optionB.setAttribute('style','display:none');
    optionB.setAttribute('style', 'opacity: 0.7');
    optionB.children[0].setAttribute('style', 'background: #CCC');
    optionB.children[0].setAttribute('style', 'cursor:default');
    let optionC = document.getElementById("optionC");
    if (optionC != null) {
        //optionC.setAttribute('style','display:none');
        optionC.setAttribute('style', 'opacity: 0.7');
        optionC.children[0].setAttribute('style', 'background: #CCC');
        optionC.children[0].setAttribute('style', 'cursor:default');
    }
    let optionD = document.getElementById("optionD");
    if (optionD != null) {
        //optionD.setAttribute('style','display:none');
        optionD.setAttribute('style', 'opacity: 0.7');
        optionD.children[0].setAttribute('style', 'background: #CCC');
        optionD.children[0].setAttribute('style', 'cursor:default');
    }
    let answerSpace = document.getElementById("answerSpace");
    //<div class="spinner-grow" style="width: 3rem; height: 3rem;" role="status">
    //  <span class="sr-only">Loading...</span>
    //</div>
    let spinner = document.createElement('div');
    spinner.id = 'spinner';
    spinner.class = 'spinner-grow';
    spinner.style = "width: 3rem; height: 3rem;"
    spinner.role = "status"
    let loader = document.createElement('span');
    loader.class = "sr-only";
    spinner.appendChild(loader);
    answerSpace.appendChild(spinner);

}

// The server derives player, correctness and timing; the client only reports the chosen option.
// Sent on click, and again on timeout (which the server ignores if the player already answered).
function saveAnswerOnAjax() {
    let selectedAnswer = document.getElementById("selectedAnswer").value;
    let xhr = new XMLHttpRequest();
    xhr.open('POST', "/answer/");
    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
    CsrfManager.addTokenToXHR(xhr);
    xhr.onload = function () {
        if (xhr.status === 200) {
            console.log('Response is ' + xhr.responseText);
            finishPracticeAnswer();
            // Show "Waiting for others" message for non-moderators in multiplayer mode
            let roles = document.getElementById("roles").value;
            let gameMode = document.getElementById('gameMode') ? document.getElementById('gameMode').value : 'MULTIPLAYER';
            if (!roles.includes("ROLE_MODERATOR") && gameMode === 'MULTIPLAYER') {
                let waitingMessage = document.getElementById('waitingMessage');
                if (waitingMessage) {
                    waitingMessage.style.display = 'block';
                }
            }
        } else if (xhr.status !== 200) {
            console.log('Request failed.  Returned status of ' + xhr.status);
        }
    };
    xhr.send('selectedAnswer=' + encodeURIComponent(selectedAnswer));
}

function showScoreboard() {
    let pin = document.getElementById('quizPin').value;
    console.log('triggering scoreboard for game pin: ' + pin);
    let socket = new SockJS('/darkhold-websocket');
    stompClient = Stomp.over(socket);

    // Add CSRF token to connection headers
    let headers = {};
    if (typeof CsrfManager !== 'undefined') {
        headers = CsrfManager.getHeadersForStomp();
    }

    stompClient.connect(headers, function (frame) {
        console.log('Connected: ' + frame);
        // Send PIN for PIN-scoped topic
        stompClient.send("/app/fetch_scores", {}, pin);
    });
}

let isPaused = false;
let pausedTimeRemaining = 0;

function connect() {
    let pin = document.getElementById('quizPin').value;
    let socket = new SockJS('/darkhold-websocket');
    stompClient = Stomp.over(socket);

    // Add CSRF token to connection headers
    let headers = {};
    if (typeof CsrfManager !== 'undefined') {
        headers = CsrfManager.getHeadersForStomp();
    }

    stompClient.connect(headers, function (frame) {
        console.log('Connected: ' + frame);
        // Subscribe to PIN-scoped topics for concurrent game support
        stompClient.subscribe('/topic/' + pin + '/read_scores', function (status) {
            console.log(status);
            gotoScoreBoard();
        });
        // Subscribe to pause/resume events (PIN-scoped)
        stompClient.subscribe('/topic/' + pin + '/game_paused', function (status) {
            console.log('Game paused');
            showPauseOverlay();
        });
        stompClient.subscribe('/topic/' + pin + '/game_resumed', function (message) {
            console.log('Game resumed, elapsed: ' + message.body + 'ms');
            hidePauseOverlay();
        });
        stompClient.subscribe('/topic/' + pin + '/question_skipped', function (status) {
            console.log('Question skipped');
            gotoScoreBoard();
        });
        stompClient.subscribe('/topic/' + pin + '/game_ended', function (status) {
            console.log('Game ended early');
            document.forms[0].action = "/final";
            document.forms[0].submit();
        });
    });
    startTime = new Date().getTime();
    endTime = new Date().getTime();
}

function togglePause() {
    let pin = document.getElementById('quizPin').value;
    if (!isPaused) {
        // Pause the game (send PIN for PIN-scoped topic)
        stompClient.send("/app/pause_game", {}, pin);
    } else {
        // Resume the game (send PIN for PIN-scoped topic)
        stompClient.send("/app/resume_game", {}, pin);
    }
}

function showPauseOverlay() {
    isPaused = true;
    // Stop the timer
    if (typeof timer !== 'undefined' && timer) {
        timer.stop();
    }
    // Save remaining time
    if (typeof clock2 !== 'undefined' && clock2) {
        pausedTimeRemaining = clock2.getTime().time;
    }
    // Show overlay
    let overlay = document.getElementById('pauseOverlay');
    if (overlay) {
        overlay.style.display = 'flex';
    }
    // Update button text
    let pauseBtn = document.getElementById('pauseButton');
    if (pauseBtn) {
        pauseBtn.textContent = 'Resume';
        pauseBtn.classList.remove('btn-warning');
        pauseBtn.classList.add('btn-success');
    }
}

function hidePauseOverlay() {
    isPaused = false;
    // Hide overlay
    let overlay = document.getElementById('pauseOverlay');
    if (overlay) {
        overlay.style.display = 'none';
    }
    // Resume the timer
    if (typeof timer !== 'undefined' && timer) {
        timer.start();
    }
    // Update button text
    let pauseBtn = document.getElementById('pauseButton');
    if (pauseBtn) {
        pauseBtn.textContent = 'Pause';
        pauseBtn.classList.remove('btn-success');
        pauseBtn.classList.add('btn-warning');
    }
}

function gotoScoreBoard() {
    document.forms[0].action = "/scoreboard";
    document.forms[0].submit();
}

/**
 * Practice Mode: Advance to next question
 */
function goToNextQuestion() {
    console.log('Practice mode: advancing to next question');
    document.forms[0].action = "/question";
    document.forms[0].submit();
}

const sortingSelection = new Set();
let sortingSubmitted = false;
let sortingClosed = false;

function chooseSortingAnswer(button) {
    if (isAnswerSelected || (document.getElementById('roles').value.includes('ROLE_MODERATOR')
            && document.getElementById('gameMode').value !== 'PRACTICE')) return;
    const value = button.dataset.value;
    if (sortingSelection.has(value)) sortingSelection.delete(value); else sortingSelection.add(value);
    const buttons = document.querySelectorAll('#sortingAnswers [data-value]');
    buttons.forEach(item => {
        const rank = [...sortingSelection].indexOf(item.dataset.value);
        item.querySelector('.sorting-rank').textContent = rank < 0 ? '–' : rank + 1;
        item.setAttribute('aria-pressed', String(rank >= 0));
    });
    document.getElementById('submitSorting').disabled = sortingSelection.size !== buttons.length;
}

async function submitSortingAnswer() {
    if (isAnswerSelected || document.getElementById('submitSorting').disabled) return;
    isAnswerSelected = true;
    sortingSubmitted = true;
    endTime = Date.now();
    document.querySelectorAll('#sortingAnswers button').forEach(button => button.disabled = true);
    try {
        const response = await fetch('/validate_answer/', {
            method: 'POST',
            headers: CsrfManager.addTokenToFetch({'Content-Type': 'application/x-www-form-urlencoded'}),
            body: new URLSearchParams({userAnswer: [...sortingSelection].join(',')})
        });
        if (!response.ok) throw new Error(LanguageManager.text('live.requestFailed'));
        document.getElementById('selectedOptions').value = await response.text();
        finishPracticeAnswer();
    } catch (error) {
        document.getElementById('sortingFeedback').textContent = error.message;
        if (!sortingClosed) {
            isAnswerSelected = false;
            sortingSubmitted = false;
            document.querySelectorAll('#sortingAnswers button').forEach(button => button.disabled = false);
        }
    }
}
