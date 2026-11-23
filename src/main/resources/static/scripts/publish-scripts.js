var teamData = [];
var allPlayers = [];

function startGame() {
    let pin = document.getElementById('quizPin').value;
    console.log('triggering quiz with pin ' + pin);
    document.getElementById('quiz_pin').value = pin;
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
        stompClient.subscribe('/topic/' + pin + '/start', function (greeting) {
            gotoMyGame();
        });
        stompClient.send("/app/start", {}, pin);
    });
}

function gotoMyGame() {
    document.forms[0].action = "/interstitial";
    document.forms[0].submit();
}

function logOut() {
    document.forms[0].action = "/logout";
    document.forms[0].submit();
}

function toOptions() {
    document.forms[0].action = "/options";
    document.forms[0].submit();
}

function toHome() {
    window.location.href = "/";
}

function connect() {
    let pin = document.getElementById('quizPin').value;
    let teamMode = document.getElementById('teamMode')?.value === 'true';
    // players who joined before this page loaded (returning to the lobby)
    showGreeting([...document.querySelectorAll('#players td')].map(cell => cell.textContent));
    let socket = new SockJS('/darkhold-websocket');
    stompClient = Stomp.over(socket);

    // Add CSRF token to connection headers
    let headers = {};
    if (typeof CsrfManager !== 'undefined') {
        headers = CsrfManager.getHeadersForStomp();
    }

    stompClient.connect(headers, function (frame) {
        // Subscribe to PIN-scoped topic for concurrent game support
        stompClient.subscribe('/topic/' + pin + '/user', function (greeting) {
            showGreeting(JSON.parse(greeting.body).users);
            if (teamMode) {
                loadTeamsAndRender();
            }
        });

        // Subscribe to team updates if in team mode
        if (teamMode) {
            stompClient.subscribe('/topic/' + pin + '/team_update', function (message) {
                console.log('Team update received');
                loadTeamsAndRender();
            });

            // Initial load of teams
            loadTeamsAndRender();
        }
    });
}

function loadTeamsAndRender() {
    let pin = document.getElementById('quizPin').value;
    RequestClient.json('/team/list/' + pin)
        .then(teams => {
            teamData = teams;
            renderTeamView();
        })
        .catch(err => console.error('Error loading teams:', err));
}

function renderTeamView() {
    let teamColumns = document.getElementById('teamColumns');
    let unassignedDiv = document.getElementById('unassignedPlayers');

    if (!teamColumns || !unassignedDiv) return;

    let assignedPlayers = new Set();
    teamData.forEach(team => {
        (team.members || []).forEach(m => assignedPlayers.add(m));
    });
    let unassigned = allPlayers.filter(p => !assignedPlayers.has(p));

    unassignedDiv.innerHTML = '';
    if (unassigned.length === 0) {
        unassignedDiv.innerHTML = '<span class="text-muted">' + escapeHtml(LanguageManager.text('client.publish.allAssigned')) + '</span>';
    } else {
        unassigned.forEach(player => unassignedDiv.appendChild(teamMemberItem(player, null)));
    }

    teamColumns.innerHTML = '';
    teamData.forEach(team => {
        let column = document.createElement('div');
        column.className = 'team-column team-column-' + team.color;
        column.innerHTML = `
            <div class="team-column-header">
                <h5 class="team-column-title">
                    <span class="team-color-dot team-color-dot-${escapeHtml(team.color)}"></span>
                    ${escapeHtml(team.name)}
                </h5>
                <div class="team-column-count">${escapeHtml(LanguageManager.text('client.publish.members', (team.members || []).length))}</div>
            </div>
            <div class="team-members-list"></div>
        `;
        let list = column.querySelector('.team-members-list');
        (team.members || []).forEach(member => list.appendChild(teamMemberItem(member, team.name)));
        teamColumns.appendChild(column);
    });
}

// A player with a dropdown to move them to another team, and a kick button
function teamMemberItem(player, currentTeam) {
    let item = document.createElement('div');
    item.className = 'team-member-item';
    let name = document.createElement('span');
    name.textContent = displayName(player);
    let select = document.createElement('select');
    select.className = 'team-assign-dropdown';
    select.setAttribute('aria-label', LanguageManager.text('client.publish.assignTo'));
    select.innerHTML = `<option value="">${escapeHtml(LanguageManager.text('client.publish.assignTo'))}</option>`
        + teamData.filter(t => t.name !== currentTeam)
            .map(t => `<option value="${escapeHtml(t.name)}">${escapeHtml(t.name)}</option>`).join('');
    select.addEventListener('change', e => assignPlayerToTeam(player, e.target.value));
    item.append(name, select, kickButton(player));
    return item;
}

function displayName(player) {
    // accounts are named "<first name>#<id>"; the lobby shows the name only
    return String(player).replace(/#\d+$/, '');
}

function kickButton(player) {
    let button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm btn-danger ms-2';
    button.textContent = LanguageManager.text('client.lobby.kick');
    button.addEventListener('click', () => kickPlayer(player));
    return button;
}

function kickPlayer(username) {
    if (!confirm(LanguageManager.text('client.lobby.kickConfirm', displayName(username)))) return;
    let pin = document.getElementById('quizPin').value;
    // the server answers with the new player list and teams
    stompClient.send("/app/kick_player", {}, pin + ':' + username);
}

function assignPlayerToTeam(username, teamName) {
    if (!teamName) return;

    let pin = document.getElementById('quizPin').value;
    fetch('/team/assign', {
        method: 'POST',
        headers: CsrfManager.addTokenToFetch({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ pin: pin, username: username, teamName: teamName })
    })
    .then(response => {
        if (response.ok) {
            loadTeamsAndRender();
        } else {
            alert(LanguageManager.text('client.request.failed'));
        }
    })
    .catch(() => alert(LanguageManager.text('client.request.network')));
}

function autoAssignAllPlayers() {
    let pin = document.getElementById('quizPin').value;
    let assignmentMethod = document.getElementById('assignmentMethod')?.value || 'BALANCED';

    // Get unassigned players
    let assignedPlayers = new Set();
    teamData.forEach(team => {
        (team.members || []).forEach(m => assignedPlayers.add(m));
    });

    let unassigned = allPlayers.filter(p => !assignedPlayers.has(p));

    if (unassigned.length === 0) {
        alert(LanguageManager.text('client.publish.allAlreadyAssigned'));
        return;
    }

    // Auto-assign each unassigned player
    let promises = unassigned.map((player, index) => {
        let teamIndex;
        if (assignmentMethod === 'RANDOM') {
            teamIndex = Math.floor(Math.random() * teamData.length);
        } else {
            // BALANCED: assign to team with fewest members
            let minCount = Math.min(...teamData.map(t => (t.members || []).length));
            teamIndex = teamData.findIndex(t => (t.members || []).length === minCount);
            // Update local count for next iteration
            teamData[teamIndex].members = teamData[teamIndex].members || [];
            teamData[teamIndex].members.push(player);
        }

        let teamName = teamData[teamIndex].name;
        return fetch('/team/assign', {
            method: 'POST',
            headers: CsrfManager.addTokenToFetch({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ pin: pin, username: player, teamName: teamName })
        }).then(response => {
            if (!response.ok) throw new Error(LanguageManager.text('client.request.failed'));
        });
    });

    Promise.all(promises)
        .then(() => loadTeamsAndRender())
        .catch(error => alert(error.message));
}

function escapeHtml(text) {
    if (!text) return '';
    let div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML.replace(/"/g, '&quot;');
}

// The host runs the game but does not play: leave them out of the players
function showGreeting(users) {
    let moderator = document.getElementById('user')?.textContent || '';
    allPlayers = (users || []).filter(user => user !== moderator);
    let grid = document.getElementById('playersGrid');
    if (!grid) return;
    grid.replaceChildren(...allPlayers.map(player => {
        let card = document.createElement('div');
        card.className = 'player-card';
        card.innerHTML = '<div class="player-avatar"><i class="fas fa-user"></i></div>'
            + '<span class="player-name">' + escapeHtml(displayName(player)) + '</span>';
        card.appendChild(kickButton(player));
        return card;
    }));
    let emptyState = document.getElementById('emptyState');
    if (emptyState) emptyState.style.display = allPlayers.length ? 'none' : 'block';
    let count = document.getElementById('playerCount');
    if (count) count.textContent = allPlayers.length;
}