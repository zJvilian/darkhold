function startGame() {
    document.forms[0].action = "/game";
    document.forms[0].submit();
}

function connect() {
    let name = document.getElementById('name').value;
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
        // Listen before asking, or the reply can arrive while nobody is subscribed
        stompClient.subscribe('/topic/' + pin + '/question_read', function (greeting) {
            updateTextOrEndGame(JSON.parse(greeting.body).startGame);
        });
        stompClient.send("/app/question_fetch", {}, pin + ":" + name);
    });
}

function endGame() {
    document.forms[0].action = "/final";
    document.forms[0].submit();
}