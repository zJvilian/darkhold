function selectQuiz(elem) {
    document.getElementById('challenges').value = elem.closest('.challenge-card').id;
    document.getElementById('challengeListForm').requestSubmit();
}

function markQuizForDeletion(elem) {
    document.getElementById('challenges').value = elem.closest('.challenge-card').id;
}

function deleteQuiz() {
    const id = document.getElementById('challenges').value;
    RequestClient.json('/delete_challenge?challenge=' + encodeURIComponent(id), {method: 'DELETE'})
        .then(() => location.reload())
        .catch(error => alert(error.message));
}

function duplicateChallenge(elem) {
    const id = elem.closest('.challenge-card').id;
    RequestClient.json('/duplicate_challenge/' + id, {method: 'POST'})
        .then(() => location.reload())
        .catch(error => alert(error.message));
}
