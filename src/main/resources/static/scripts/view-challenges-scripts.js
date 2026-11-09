function selectQuiz(elem) {
    document.getElementById('challenges').value = elem.closest('.quiz-row').id;
    document.getElementById('challengeListForm').requestSubmit();
}

function markQuizForDeletion(elem) {
    document.getElementById('challenges').value = elem.closest('.quiz-row').id;
}

function deleteQuiz() {
    const id = document.getElementById('challenges').value;
    RequestClient.json('/delete_challenge?challenge=' + encodeURIComponent(id), {method: 'DELETE'})
        .then(() => location.reload())
        .catch(error => alert(error.message));
}

function duplicateChallenge(elem) {
    const id = elem.closest('.quiz-row').id;
    RequestClient.json('/duplicate_challenge/' + id, {method: 'POST'})
        .then(() => location.reload())
        .catch(error => alert(error.message));
}

/* Client-side search, owner filter and sorting of the shared quiz list. */
const QuizList = (() => {
    const SORTS = {
        nameAsc: (a, b) => a.title.localeCompare(b.title),
        nameDesc: (a, b) => b.title.localeCompare(a.title),
        newest: (a, b) => b.created.localeCompare(a.created),
        oldest: (a, b) => a.created.localeCompare(b.created),
        questionsDesc: (a, b) => b.questions - a.questions,
        questionsAsc: (a, b) => a.questions - b.questions
    };
    let rows = [];

    function init() {
        const list = document.getElementById('quizRows');
        if (!list) return;
        rows = Array.from(list.querySelectorAll('.quiz-row')).map(el => ({
            el,
            title: el.dataset.title || '',
            text: ((el.dataset.title || '') + ' ' + (el.dataset.description || '')).toLowerCase(),
            owner: el.dataset.owner || '',
            created: el.dataset.created || '',
            questions: Number(el.dataset.questions) || 0
        }));
        fillOwners();
        ['quizSearch', 'quizSort', 'quizOwner'].forEach(id => {
            const control = document.getElementById(id);
            control.addEventListener('input', apply);
            control.addEventListener('change', apply);
        });
        document.getElementById('quizSearch').addEventListener('keydown', event => {
            if (event.key === 'Enter') event.preventDefault();
        });
        list.querySelectorAll('.quiz-row-main[data-selectable]').forEach(main => {
            main.setAttribute('role', 'button');
            main.tabIndex = 0;
            main.addEventListener('click', () => selectQuiz(main));
            main.addEventListener('keydown', event => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    selectQuiz(main);
                }
            });
        });
        apply();
    }

    function fillOwners() {
        const owners = [...new Set(rows.map(r => r.owner).filter(Boolean))].sort((a, b) => a.localeCompare(b));
        if (owners.length < 2) return;
        const select = document.getElementById('quizOwner');
        owners.forEach(name => select.add(new Option(name, name)));
        document.getElementById('quizOwnerField').hidden = false;
    }

    function apply() {
        const term = document.getElementById('quizSearch').value.trim().toLowerCase();
        const owner = document.getElementById('quizOwner').value;
        const sorted = rows.slice().sort(SORTS[document.getElementById('quizSort').value] || SORTS.nameAsc);
        const list = document.getElementById('quizRows');
        let shown = 0;
        sorted.forEach(row => {
            const visible = row.text.includes(term) && (!owner || row.owner === owner);
            row.el.hidden = !visible;
            if (visible) shown++;
            list.appendChild(row.el);
        });
        document.getElementById('quizCount').textContent = LanguageManager.text('client.quizList.count', shown, rows.length);
        document.getElementById('quizNoMatches').hidden = shown > 0;
    }

    return {init};
})();

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', QuizList.init);
else QuizList.init();
