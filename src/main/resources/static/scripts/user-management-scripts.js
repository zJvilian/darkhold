function toOptions() {
    document.forms[0].action = "/options";
    document.forms[0].submit();
}

function logOut() {
    document.forms[0].action = "/logout";
    document.forms[0].submit();
}

function checkEmailUnique(form) {
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/users/check_email", true);
    xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
    CsrfManager.addTokenToXHR(xhr);
    xhr.timeout = 15000;
    const fail = key => {
        button.disabled = false;
        showModelDialog(document.getElementById('modalTitle').textContent, LanguageManager.text(key));
    };
    xhr.onload = () => {
        if (xhr.status === 200 && xhr.responseText === 'OK') {
            form.submit();
        } else if (xhr.status === 200 && xhr.responseText === 'DUPLICATED') {
            fail('client.user.emailDuplicate');
        } else if (xhr.status === 403) {
            fail('client.request.forbidden');
        } else if (xhr.status === 401 || xhr.responseURL.includes('/login')) {
            fail('client.request.login');
        } else {
            fail('client.request.failed');
        }
    };
    xhr.onerror = xhr.ontimeout = () => fail('client.request.network');
    xhr.send(new URLSearchParams({
        email: form.elements.namedItem('email').value,
        id: form.elements.namedItem('id').value
    }).toString());
    return false;
}
