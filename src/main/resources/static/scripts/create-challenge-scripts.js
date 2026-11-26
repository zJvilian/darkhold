// Create quiz page: step 1 of the creation flow. Whatever the source, a successful create
// sends the user straight into the question editor, where the new quiz waits as a draft.

const SOURCE_ACCEPT = {file: ['.xlsx', '.csv'], json: ['.json']};

function selectedSource() {
    const checked = document.querySelector('input[name="source"]:checked');
    return checked ? checked.value : 'empty';
}

function selectSource(source) {
    const panel = document.getElementById('filePanel');
    const upload = document.getElementById('upload');
    const hint = document.getElementById('dropzoneHint');
    const importing = source === 'file' || source === 'json';
    panel.hidden = !importing;
    if (importing) {
        upload.accept = SOURCE_ACCEPT[source].join(',');
        hint.textContent = source === 'json' ? hint.dataset.json : hint.dataset.xlsx;
        if (upload.files && upload.files.length > 0 && !hasExtension(upload.files[0], SOURCE_ACCEPT[source])) {
            upload.value = '';
        }
        showChosenFile();
    }
    document.getElementById('excelGuide').hidden = source !== 'file';
    // a JSON export brings its own title, so the field becomes optional there
    const json = source === 'json';
    document.getElementById('title').required = !json;
    document.getElementById('titleLabel').classList.toggle('form-label-required', !json);
    document.getElementById('titleHint').hidden = json;
    document.getElementById('jsonTitleHint').hidden = !json;
    document.getElementById('resultMessage').replaceChildren();
}

function hasExtension(file, extensions) {
    const name = (file && file.name ? file.name : '').toLowerCase();
    return extensions.some(ext => name.endsWith(ext));
}

function showChosenFile() {
    const files = document.getElementById('upload').files;
    const chosen = files && files.length > 0 ? files[0] : null;
    document.getElementById('dropzoneFile').hidden = !chosen;
    document.getElementById('dropzoneFileName').textContent = chosen ? chosen.name : '';
    document.getElementById('dropzone').classList.toggle('has-file', !!chosen);
}

function uploadPDF() {
    const uploadButton = document.getElementById('btnSubmit');
    const title = document.getElementById('title').value.trim();
    const description = document.getElementById('description').value.trim();
    const resultDiv = document.getElementById('resultMessage');
    const source = selectedSource();
    const files = document.getElementById('upload').files;
    const file = files && files.length > 0 ? files[0] : null;

    if (!title && source !== 'json') {
        showChallengeMessage(resultDiv, LanguageManager.text('client.create.titleRequired'), 'danger');
        document.getElementById('title').focus();
        return undefined;
    }
    if (source === 'empty') {
        return createEmptyChallenge(uploadButton, title, description, resultDiv);
    }
    if (!file) {
        showChallengeMessage(resultDiv, LanguageManager.text('client.create.chooseFile'), 'danger');
        return undefined;
    }
    if (!hasExtension(file, SOURCE_ACCEPT[source])) {
        showChallengeMessage(resultDiv, LanguageManager.text(
            source === 'json' ? 'client.create.wrongFileJson' : 'client.create.wrongFileXlsx'), 'danger');
        return undefined;
    }
    return source === 'json'
        ? importJson(uploadButton, file, title, description, resultDiv)
        : uploadWithExcel(uploadButton, file, title, description, resultDiv);
}

function setBusy(button, labelKey) {
    if (button.dataset && button.dataset.label === undefined) {
        button.dataset.label = button.innerHTML;
    }
    button.disabled = true;
    button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + LanguageManager.text(labelKey);
}

function resetBusy(button) {
    button.disabled = false;
    button.innerHTML = button.dataset && button.dataset.label !== undefined
        ? button.dataset.label : LanguageManager.text('client.create.create');
}

function uploadWithExcel(uploadButton, file, title, description, resultDiv) {
    const progress = document.getElementById('progress');
    const progressDiv = document.getElementById('progressdiv');

    progressDiv.style.display = 'block';
    setBusy(uploadButton, 'client.create.uploading');

    const formData = new FormData();
    formData.append('upload', file, file.name);
    formData.append('title', title);
    formData.append('description', description);

    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/upload_challenge', true);
    xhr.responseType = 'json';
    CsrfManager.addTokenToXHR(xhr);

    xhr.upload.onprogress = function (e) {
        if (e.lengthComputable) {
            const percentage = Math.round((e.loaded / e.total) * 100);
            progress.style.width = percentage + '%';
            progress.setAttribute('aria-valuenow', percentage);
            uploadButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> '
                + LanguageManager.text('client.create.uploadPercent', percentage);
        }
    };

    xhr.onload = function () {
        progressDiv.style.display = 'none';
        try {
            const data = validateChallengeResponse(xhr.status, xhr.response);
            openEditor(resultDiv, data.challengeId);
        } catch (error) {
            showChallengeMessage(resultDiv, error.message, 'danger');
            resetBusy(uploadButton);
        }
    };

    xhr.onerror = function () {
        progressDiv.style.display = 'none';
        showChallengeMessage(resultDiv, LanguageManager.text('client.create.network'), 'danger');
        resetBusy(uploadButton);
    };

    xhr.send(formData);
}

function createEmptyChallenge(uploadButton, title, description, resultDiv) {
    setBusy(uploadButton, 'client.create.creating');

    const formData = new FormData();
    formData.append('title', title);
    formData.append('description', description);

    return fetch('/save_challenge', {
        method: 'POST',
        headers: CsrfManager.addTokenToFetch(),
        body: formData
    })
    .then(parseChallengeResponse)
    .then(data => openEditor(resultDiv, data.challengeId))
    .catch(error => {
        showChallengeMessage(resultDiv, error.message, 'danger');
        resetBusy(uploadButton);
    });
}

function importJson(button, file, title, description, resultDiv) {
    setBusy(button, 'client.create.uploading');
    const formData = new FormData();
    formData.append('file', file, file.name);
    if (title) {
        formData.append('title', title);
    }
    if (description) {
        formData.append('description', description);
    }
    return fetch('/import_challenge/json', {
        method: 'POST',
        headers: CsrfManager.addTokenToFetch(),
        body: formData
    })
    .then(parseChallengeResponse)
    .then(data => openEditor(resultDiv, data.challengeId))
    .catch(error => {
        showChallengeMessage(resultDiv, error.message, 'danger');
        resetBusy(button);
    });
}

// Step 2: the new quiz is a draft; continue in the question editor
function openEditor(resultDiv, challengeId) {
    showChallengeMessage(resultDiv, LanguageManager.text('client.create.opening'), 'success');
    window.location.href = '/edit_challenge/' + challengeId;
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

function updateCharCounter(counter) {
    const field = document.getElementById(counter.dataset.counterFor);
    if (field) {
        counter.textContent = field.value.length + ' / ' + field.maxLength;
        counter.classList.toggle('is-near-limit', field.value.length >= field.maxLength * 0.9);
    }
}

function initCreatePage() {
    const dropzone = document.getElementById('dropzone');
    if (!dropzone) {
        return;
    }
    document.querySelectorAll('.char-counter').forEach(counter => {
        const field = document.getElementById(counter.dataset.counterFor);
        field.addEventListener('input', () => updateCharCounter(counter));
        updateCharCounter(counter);
    });
    ['dragenter', 'dragover'].forEach(type => dropzone.addEventListener(type, event => {
        event.preventDefault();
        dropzone.classList.add('is-dragging');
    }));
    ['dragleave', 'drop'].forEach(type => dropzone.addEventListener(type, event => {
        event.preventDefault();
        dropzone.classList.remove('is-dragging');
    }));
    dropzone.addEventListener('drop', event => {
        if (event.dataTransfer && event.dataTransfer.files.length > 0) {
            document.getElementById('upload').files = event.dataTransfer.files;
            showChosenFile();
        }
    });
    // keep the panel in sync when the browser restores a previously chosen option
    selectSource(selectedSource());
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initCreatePage);
} else {
    initCreatePage();
}
