<h1 align="center">
    Kahvoid
</h1>

Self-hosted real-time quiz platform, inspired by `kahoot`.

> Kahvoid is a fork of [Darkhold](https://github.com/surajcm/darkhold) by [Suraj Muraleedharan](https://github.com/surajcm) (Apache License 2.0).

## 🐋 Docker

Build and start this checkout with PostgreSQL:

```sh
cp .env.example .env   # then set POSTGRES_PASSWORD (required) and DARKHOLD_ADMIN_PASSWORD
docker compose up -d --build --wait
```

`POSTGRES_PASSWORD` has no default. If you already have a `postgres_data` volume, use the password it
was created with (previously `darkhold`). On first start the seeded `admin@admin.com` account gets
`DARKHOLD_ADMIN_PASSWORD`, or a random password logged once at WARN if unset (`docker compose logs darkhold`).
The database port is not published; only the app is reachable on 8181.

Open http://localhost:8181. Check readiness with `curl -f http://localhost:8181/actuator/health`.
The image compiles the application during the build; startup runs the packaged JAR.
Set `JAVA_OPTS` in Compose to change JVM options. PostgreSQL data stays in the `postgres_data` volume.

For a temporary H2 development instance, use `docker compose -f docker-compose.h2.yml up -d --build --wait`.
Stop the PostgreSQL stack first to free port 8181. H2 data is in memory and resets when the container restarts.

Run checks with `./gradlew build` (JDK 25) and `node --test src/test/javascript/*.test.cjs` (Node.js 24+).

## Host a live quiz; guests need no account

1. Sign in as a host, create a quiz, and add questions in the editor.
2. Click **Host live quiz** in the editor (or **Start Multiplayer** with team mode off).
3. Players open `/join`, enter the six-digit room code, choose a nickname and an avatar.
   Scanning the host's QR code or opening **Copy invite link** fills in the code for them.
4. Start when everyone is ready. Players answer on their own devices. The host reveals answers,
   advances through the leaderboard, and finishes on the podium.

Players never provide an email or password. A private HTTP-only cookie remembers their place on
their browser; refreshing or reconnecting preserves it. Nicknames are unique within a room. The host
can lock the lobby or remove a player. Scores, deadlines, and one-answer-per-question rules are
enforced on the server. Multiple rooms run independently. True/false, multiple choice (including
multiple correct answers), typed answers, and polls use the existing question editor.

**Joining from phones:** `localhost` only works on the device opening it. On the same Wi-Fi,
open the host screen using your computer's LAN address, e.g. `http://192.168.1.20:8181`.
The invite and QR code then use that address. Alternatively, copy `.env.example` to `.env`,
set `DARKHOLD_PUBLIC_BASE_URL` to the reachable LAN or public HTTPS address (no trailing slash),
and run `docker compose up -d`. This also makes QR codes work when the host opens localhost.
For a public deployment, use your existing HTTPS reverse proxy URL.

The classic live flow persists rooms and results in PostgreSQL for 24 hours, including container
restarts. Return via **Active Games** to resume hosting or view a podium. Expired rooms are removed
hourly. H2 development rooms reset on restart. Live updates poll once per second. Existing team
and practice modes keep their original flow; the new classic room results are available on its
podium rather than in the legacy analytics reports.

The optional browser smoke check is `node src/test/browser/live-quiz.cjs` with Playwright installed
and Chromium available (`CHROMIUM_PATH` can select an installed browser). It uses separate host
and mobile guest contexts, exercises every question type, and writes screenshots to
`/tmp/darkhold-live-smoke`. Set `DARKHOLD_TEST_URL`, `DARKHOLD_TEST_USERNAME`, and
`DARKHOLD_TEST_PASSWORD` for a different test instance. Only on a local test deployment, set
`DARKHOLD_TEST_RESTART=1` to verify guest recovery after `docker compose restart darkhold`.
The temporary quiz is deleted afterward; its completed room expires normally after 24 hours.
With the same browser setup, `node src/test/browser/german-language.cjs` checks the DE selector,
language persistence, quiz editing, and an anonymous German game on a mobile-sized screen.
`node src/test/browser/user-management.cjs` checks guest permission denials and administrator
deletion, including CSRF protection and readable confirmation dialogs in both themes.

## 🤝 Contributing

Contributions, issues and feature requests are welcome!
If you contribute please commit to a new branch and explain details in your pull request not in your commit.

Remember to abide by our [Code of Conduct](CODE_OF_CONDUCT.md) too.

## ✨ Key Features

### 🎮 Game Modes
- **Multiplayer** - Real-time quiz with live PIN-based join
- **Practice Mode** - Solo play with instant feedback
- **Team Mode** - Collaborative gameplay with 3 assignment methods (Balanced, Random, Manual)

### 📝 Question Types
- Multiple Choice (2-4 options, single or multiple correct answers)
- True/False
- Type Answer (with fuzzy matching for typos)
- Poll (no correct answer, shows distribution)

### 🎨 Rich Content
- Image support for questions
- YouTube video embedding
- Configurable time limits per question
- Customizable point values

### 📊 Analytics & Reports
- Detailed game results with participant statistics
- Question-level analytics (success rate, difficulty)
- CSV export for external analysis
- Past games history

### 🌍 Internationalization
- **English** (en) - Complete ✅
- **Spanish** (es) - Complete ✅
- **French** (fr) - Partial (contributors welcome!)
- **German** (de) - Quiz creation, editor, host and guest live-game flow

Choose **Deutsch** in the banner’s language menu to switch to **DE**. The choice
persists across pages, login and browser sessions. Live-game pages also have a
language selector; quiz questions and player names keep their original text.

### 🎨 Modern UI/UX
- Dark/Light mode toggle
- Sound effects and animations
- PWA support for mobile
- Fully responsive design
- Accessibility features (ARIA, keyboard navigation)

### 🔧 Technical
- Spring Boot 4.1 with WebSocket/STOMP for real-time gameplay
- PostgreSQL or H2 database support
- Docker & Docker Compose ready
- Comprehensive test suite (427 tests, 51% coverage)

For detailed installation guides and system requirements, see [HOW-TO.md](HOW-TO.md).
