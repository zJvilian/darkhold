const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function setupEditor() {
    const elements = {};
    const getEl = (id) => {
        if (!elements[id]) {
            elements[id] = {
                id,
                style: {},
                className: '',
                value: '',
                checked: false,
                files: [],
                parentElement: { style: {} }
            };
        }
        return elements[id];
    };

    // Pre-populate elements used in editchallenge.html
    ['questionType', 'multipleChoiceAnswers', 'trueFalseAnswers', 'typeAnswerInput',
     'wordCloudSettings', 'pointsCol', 'questionTypeCol', 'timeLimitCol', 'points',
     'correctAnswerHint', 'answerCGroup', 'answerDGroup', 'questionText',
     'timeLimit', 'videoUrl', 'editingQuestionId', 'wordCloudQuestionFuzzy',
     'answer1', 'answer2', 'answer3', 'answer4', 'typeAnswerCorrect',
     'acceptableAnswers', 'tfTrue', 'tfFalse', 'correctA', 'correctB', 'correctC', 'correctD',
     'questionImageInput'].forEach(getEl);

    // Default values
    getEl('questionType').value = 'MULTIPLE_CHOICE';
    getEl('points').value = '1000';
    getEl('wordCloudQuestionFuzzy').checked = true;

    let savedData = null;
    const context = vm.createContext({
        console,
        challengeId: 101,
        window: { currentImageUrl: null },
        document: {
            getElementById: (id) => getEl(id)
        },
        LanguageManager: {
            text: (k) => k
        },
        CsrfManager: {
            addTokenToFetch: (h = {}) => h
        },
        RequestClient: {
            json: async (url, opts) => {
                savedData = JSON.parse(opts.body);
                return { id: 1 };
            }
        },
        location: { reload: () => {} },
        alert: () => {}
    });

    // Extract onQuestionTypeChange and saveQuestion functions from editchallenge.html
    const html = fs.readFileSync('src/main/resources/templates/challenge/editchallenge.html', 'utf8');
    const onTypeChangeMatch = html.match(/function onQuestionTypeChange\(\) \{[\s\S]*?\n\}/);
    const saveQuestionMatch = html.match(/function saveQuestion\(\) \{[\s\S]*?\n\}/);
    assert.ok(onTypeChangeMatch, 'onQuestionTypeChange function found');
    assert.ok(saveQuestionMatch, 'saveQuestion function found');

    vm.runInContext(onTypeChangeMatch[0], context);
    vm.runInContext(saveQuestionMatch[0], context);

    return { getEl, context, getSavedData: () => savedData };
}

test('word cloud removes points option and rebalances layout in question modal', () => {
    const { getEl, context } = setupEditor();

    // Default state: MULTIPLE_CHOICE
    context.onQuestionTypeChange();
    assert.equal(getEl('pointsCol').style.display, 'block');
    assert.equal(getEl('questionTypeCol').className, 'col-md-4');
    assert.equal(getEl('timeLimitCol').className, 'col-md-4');
    assert.equal(getEl('wordCloudSettings').style.display, 'none');

    // Switch to WORD_CLOUD
    getEl('questionType').value = 'WORD_CLOUD';
    context.onQuestionTypeChange();
    assert.equal(getEl('pointsCol').style.display, 'none', 'pointsCol should be hidden for WORD_CLOUD');
    assert.equal(getEl('questionTypeCol').className, 'col-md-6', 'questionTypeCol should expand to col-md-6');
    assert.equal(getEl('timeLimitCol').className, 'col-md-6', 'timeLimitCol should expand to col-md-6');
    assert.equal(getEl('points').value, '0', 'points should be 0 for WORD_CLOUD');
    assert.equal(getEl('points').disabled, true);
    assert.equal(getEl('wordCloudSettings').style.display, 'block', 'wordCloudSettings should be visible');

    // Switch back to MULTIPLE_CHOICE
    getEl('questionType').value = 'MULTIPLE_CHOICE';
    context.onQuestionTypeChange();
    assert.equal(getEl('pointsCol').style.display, 'block', 'pointsCol should be restored');
    assert.equal(getEl('questionTypeCol').className, 'col-md-4');
    assert.equal(getEl('timeLimitCol').className, 'col-md-4');
    assert.equal(getEl('points').value, '1000', 'points should be restored to 1000');
});

test('saving a word cloud question sets points to 0 and records typo tolerance mode', async () => {
    const { getEl, context, getSavedData } = setupEditor();

    getEl('questionType').value = 'WORD_CLOUD';
    getEl('questionText').value = 'What comes to mind?';
    getEl('wordCloudQuestionFuzzy').checked = true;

    context.onQuestionTypeChange();
    await context.saveQuestion();

    const data = getSavedData();
    assert.ok(data, 'Question request data submitted');
    assert.equal(data.questionType, 'WORD_CLOUD');
    assert.equal(data.points, 0, 'Word cloud points must be 0');
    assert.equal(data.correctOptions, 'FUZZY', 'Fuzzy typo matching correctly recorded');
});

test('sorting saves the explicit order and rejects repeated or missing answers', async () => {
    const { getEl, context, getSavedData } = setupEditor();
    getEl('questionType').value = 'SORTING';
    getEl('answer1').value = 'Alpha';
    getEl('answer2').value = 'Beta';
    getEl('answer3').value = 'Gamma';
    context.onQuestionTypeChange();
    assert.equal(getEl('sortingSettings').style.display, 'block');
    assert.equal(getEl('correctA').parentElement.style.display, 'none');
    for (const invalid of ['A,B', 'A,A,B', 'A,B,C,D']) {
        getEl('sortingOrder').value = invalid;
        await context.saveQuestion();
        assert.equal(getSavedData(), null);
    }
    getEl('sortingOrder').value = 'c, a, b';
    await context.saveQuestion();
    assert.equal(getSavedData().correctOptions, 'C,A,B');
    assert.equal(getSavedData().questionType, 'SORTING');
    assert.equal(getSavedData().points, 1000);
});

test('audio media is only accepted for MULTIPLE_CHOICE, TRUE_FALSE, and TYPE_ANSWER', () => {
    const { getEl, context } = setupEditor();
    for (const allowed of ['MULTIPLE_CHOICE', 'TRUE_FALSE', 'TYPE_ANSWER']) {
        getEl('questionType').value = allowed;
        context.onQuestionTypeChange();
        assert.ok(getEl('questionImageInput').accept.includes('audio/*'), `${allowed} should accept audio`);
    }
    context.window.currentImageUrl = '/question-images/1/2/audio_1.mp3';
    for (const disallowed of ['POLL', 'SORTING', 'WORD_CLOUD']) {
        getEl('questionType').value = disallowed;
        context.onQuestionTypeChange();
        assert.ok(!getEl('questionImageInput').accept.includes('audio/*'), `${disallowed} must not accept audio`);
        assert.equal(context.window.currentImageUrl, null);
    }
});
