function uploadPDF() {
    const uploadButton = document.getElementById('btnSubmit');
    const fileSelect = document.getElementById('upload');
    const title = document.getElementById('title').value.trim();
    const description = document.getElementById('description').value.trim();
    const resultDiv = document.getElementById('resultMessage');
    const files = fileSelect.files;

    // Validate title
    if (!title) {
        showChallengeMessage(resultDiv, LanguageManager.text('client.create.titleRequired'), 'danger');
        return;
    }

    // Check if file is present
    const hasFile = files && files.length > 0;

    if (hasFile) {
        // Scenario 1: Upload Excel file with questions
        uploadWithExcel(uploadButton, files[0], title, description, resultDiv);
    } else {
        // Scenario 2: Create empty challenge without file
        createEmptyChallenge(uploadButton, title, description, resultDiv);
    }
}

function uploadWithExcel(uploadButton, file, title, description, resultDiv) {
    const progress = document.getElementById('progress');
    const progressDiv = document.getElementById('progressdiv');

    // Show progress bar and update button
    progressDiv.style.display = "block";
    uploadButton.disabled = true;
    uploadButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + LanguageManager.text('client.create.uploading');

    const formData = new FormData();
    formData.append('upload', file, file.name);
    formData.append('title', title);
    formData.append('description', description);

    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/upload_challenge', true);
    xhr.responseType = 'json';
    CsrfManager.addTokenToXHR(xhr);

    // Track upload progress
    xhr.upload.onprogress = function (e) {
        if (e.lengthComputable) {
            const percentage = Math.round((e.loaded / e.total) * 100);
            progress.style.width = percentage + '%';
            progress.setAttribute('aria-valuenow', percentage);
            uploadButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + LanguageManager.text('client.create.uploadPercent', percentage);
        }
    };

    xhr.onload = function () {
        progressDiv.style.display = 'none';
        try {
            const data = validateChallengeResponse(xhr.status, xhr.response);
            uploadButton.style.display = 'none';
            showChallengeMessage(resultDiv, LanguageManager.text('client.create.success'), 'success');
            editChallengeLink(resultDiv, data.challengeId);
            viewChallenge(resultDiv, data.challengeId);
        } catch (error) {
            showChallengeMessage(resultDiv, error.message, 'danger');
            uploadButton.disabled = false;
            uploadButton.innerHTML = '<i class="fas fa-plus-circle"></i> ' + LanguageManager.text('client.create.create');
        }
    };

    xhr.onerror = function () {
        progressDiv.style.display = "none";
        showChallengeMessage(resultDiv, LanguageManager.text('client.create.network'), 'danger');
        uploadButton.disabled = false;
        uploadButton.innerHTML = '<i class="fas fa-plus-circle"></i> ' + LanguageManager.text('client.create.create');
    };

    xhr.send(formData);
}

function createEmptyChallenge(uploadButton, title, description, resultDiv) {
    uploadButton.disabled = true;
    uploadButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + LanguageManager.text('client.create.creating');

    const formData = new FormData();
    formData.append('title', title);
    formData.append('description', description);

    return fetch('/save_challenge', {
        method: 'POST',
        headers: CsrfManager.addTokenToFetch(),
        body: formData
    })
    .then(parseChallengeResponse)
    .then(data => {
        showChallengeMessage(resultDiv, LanguageManager.text('client.create.success'), 'success');
        uploadButton.style.display = 'none';

        editChallengeLink(resultDiv, data.challengeId);
    })
    .catch(error => {
        showChallengeMessage(resultDiv, error.message, 'danger');
        uploadButton.disabled = false;
        uploadButton.innerHTML = '<i class="fas fa-plus-circle"></i> ' + LanguageManager.text('client.create.create');
    });
}

function importJson() {
    const button = document.getElementById('btnImportJson');
    const files = document.getElementById('jsonUpload').files;
    const resultDiv = document.getElementById('jsonResultMessage');
    if (!files || files.length === 0) {
        showChallengeMessage(resultDiv, LanguageManager.text('client.create.chooseJson'), 'danger');
        return Promise.resolve();
    }
    button.disabled = true;
    const formData = new FormData();
    formData.append('file', files[0], files[0].name);
    return fetch('/import_challenge/json', {
        method: 'POST',
        headers: CsrfManager.addTokenToFetch(),
        body: formData
    })
    .then(parseChallengeResponse)
    .then(data => {
        showChallengeMessage(resultDiv, LanguageManager.text('client.create.success'), 'success');
        button.style.display = 'none';
        editChallengeLink(resultDiv, data.challengeId);
    })
    .catch(error => {
        showChallengeMessage(resultDiv, error.message, 'danger');
        button.disabled = false;
    });
}

async function parseChallengeResponse(response) {
    const isJson = (response.headers.get('content-type') || '').includes('application/json');
    const data = isJson ? await response.json().catch(() => null) : null;
    return validateChallengeResponse(response.status, data, response.redirected);
}

function validateChallengeResponse(status, data, redirected = false) {
    if (redirected || status === 401 || status === 403) {
        throw new Error(LanguageManager.text('client.create.expired'));
    }
    if (status < 200 || status >= 300 || !data || !(data.challengeId > 0)) {
        throw new Error(data?.message || LanguageManager.text('client.create.failed'));
    }
    return data;
}

function showChallengeMessage(resultDiv, message, type) {
    const alert = document.createElement('div');
    alert.className = 'alert alert-' + type;
    alert.textContent = message;
    resultDiv.replaceChildren(alert);
}

function editChallengeLink(elem, challengeId) {
    const editBtn = document.createElement('a');
    editBtn.href = '/edit_challenge/' + challengeId;
    editBtn.className = 'btn btn-success';
    editBtn.innerHTML = '<i class="fas fa-plus-circle"></i> ' + LanguageManager.text('client.create.add');
    elem.appendChild(editBtn);
}

function viewChallenge(elem, challengeId) {
    const viewButton = document.createElement('a');
    viewButton.setAttribute("class", "btn btn-primary ms-2");
    viewButton.setAttribute("href", "/preconfigure?challenges=" + challengeId);
    viewButton.innerHTML = '<i class="fas fa-eye"></i> ' + LanguageManager.text('client.create.view');
    elem.appendChild(viewButton);
}
