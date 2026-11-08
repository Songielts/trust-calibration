# Trust Calibration Study Platform

*README prose: about 880 words (code and commands not counted).*

A web system for studying whether showing an AI agent's **capability** (its accuracy in a topic), alone or with its **step-by-step reasoning**, helps people trust the agent as much as it deserves — no more, no less. People answer 16 everyday questions, see the agent's advice, and decide whether to follow it; the system records their reliance and trust and computes trust-calibration measures for a researcher dashboard. It implements the research question from my Assignment 1 review.

Detailed design notes, measures and API: [`docs/study-design.md`](docs/study-design.md).

## Project status

| Part | Status |
|---|---|
| Participant flow: consent → background questions → 16 two-stage questions (own answer first, then advice) → questionnaire → debrief | Implemented |
| 2 × 2 conditions (capability signal × reasoning), balanced assignment, demo links to preview a condition | Implemented |
| Scripted agent: answers written in advance, 12/16 correct, accuracy differs by topic | Implemented — the agent is **simulated** in this mode |
| Capability figures in scripted mode (e.g. "92%", "RetailQA-Bench") | **Simulated** study material; the benchmark names are invented |
| LLM agent (DeepSeek, any OpenAI-compatible API): live and pre-generated modes, JSON output validation, fallbacks | Implemented; tested with a fake model and a local mock server — TODO (Yutai): confirm with the real DeepSeek API |
| Evaluation runs that **measure** the LLM's accuracy per topic and use it as the capability signal | Implemented — TODO (Yutai): first real run |
| Researcher dashboard: reliance and calibration measures by condition, topic and time; CSV export | Implemented |
| Synthetic participants for previewing the dashboard | Implemented; output is **simulated** data, never findings |
| Pilot sessions with real people | Planned |
| Repeated use over several weeks; multi-step agent tasks; harder questions so the LLM makes more errors | Planned |

## Technology

| Layer | Technology | Role |
|---|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Data JPA | REST API, experiment logic, analytics |
| Database | MySQL 8 (H2 in-memory profile for quick demos) | Participants, trials, questionnaire, logged model calls |
| Frontend | Vue 3, Vite, Vue Router, Element Plus | Participant pages and researcher dashboard |
| AI model in the system | DeepSeek `deepseek-chat` via its OpenAI-compatible API (configurable) | Generates the agent's answer and reasoning in the LLM modes |
| AI tool used for development | Claude (Anthropic), via claude.ai | See *AI assistance* below |
| Tests / deployment | JUnit 5, Docker Compose, nginx | `mvn test`; one-command start |

## Run or inspect

**Quickest (Docker Desktop):**
```bash
docker compose up -d --build
```
Study at http://localhost:8081, dashboard at http://localhost:8081/admin (token `admin123`).

**Local development** (JDK 17+, Maven, Node 18+, MySQL 8):
1. Set your MySQL password in `backend/src/main/resources/application.yml` (or `DB_PASSWORD`). The database and the 16 tasks are created automatically.
2. Backend: `cd backend` → `mvn spring-boot:run` (check http://localhost:8080/api/health). No MySQL? add `-Dspring-boot.run.profiles=h2`.
3. Frontend: `cd frontend` → `npm install` → `npm run dev`, then open http://localhost:5173 (dashboard: `/admin`).

**LLM agent (optional).** Without a key everything runs with the scripted agent. To use DeepSeek, provide **your own** key — never commit it:
- copy `backend/local-secrets.example.yml` to `backend/local-secrets.yml` (git-ignored) and paste the key, **or**
- set the environment variable `LLM_API_KEY` (for Docker: a `.env` file next to `docker-compose.yml`, also git-ignored).

Then on the dashboard → **AI agent**: send a test call → run an evaluation → freeze it → choose the agent mode.

**Preview a condition:** `/?demo=CAPABILITY_REASONING` (also `CONTROL`, `CAPABILITY`, `REASONING`; `/?demo=off` to stop). Demo sessions are excluded from the analysis.

**Tests:** `cd backend` → `mvn test` (24 tests, no database or API key needed).

## Development log

| When | Change | Why | Checked |
|---|---|---|---|
| Oct (A1) | Literature review and research question | Prior work tests explanations and uncertainty, but not capability signals | — |
| 29 Oct | **v1**: scripted 2 × 2 study platform, two-stage trials, trust questionnaire, dashboard | A controlled agent is needed to know when trust *should* be high or low | In-memory test of the full flow and every measure against hand-computed values |
| 29 Oct | Compared v1 with a second, simpler prototype (TrustLens) | To choose a base | Kept v1: TrustLens had no trust measures, participants chose their own condition, and its 3 tasks had a capability figure unrelated to real accuracy |
| 29 Oct | **v2**: demo links, Docker, JUnit tests, pinned versions | Taken from the comparison: a condition must be easy to show without corrupting the data | 11 JUnit tests |
| 1 Nov | **v3**: real LLM agent (DeepSeek) alongside the scripted agent; measured capability; prompt log | Weeks 3–4: reliability is a property of the system, so the capability signal should be measured, not claimed; the scripted mode stays for experimental control | 13 more tests with a fake model; HTTP client checked against a local mock server |
| TODO | First local run on Windows | — | TODO (Yutai): what worked, what you fixed |
| TODO | First DeepSeek evaluation run | — | TODO (Yutai): validity %, accuracy per topic, any prompt changes (v2, v3…) |

Commits: TODO (Yutai) — link the commits or tags for v1, v2, v3 and your own changes.

## AI assistance

I used **Claude (Anthropic, claude.ai)** throughout:
- **Code**: asked it to implement the study from my A1 review with Spring Boot + Vue + MySQL and English UI text; it generated most of the backend and frontend code and tests.
- **Design decisions**: asked it to compare its version with another prototype and to explain the trade-offs; I chose to keep both a scripted and a real LLM agent and to use DeepSeek.
- **Mapping to lectures**: asked how the design relates to the Weeks 1–4 concepts.
- **README and report drafts.**

What I checked or changed: TODO (Yutai) — e.g. ran it locally and fixed X; read and edited the 16 tasks; checked each frozen LLM answer against the human criteria; rewrote the report in my own words.

## Additional files

- `docs/study-design.md` — experimental design, measures, LLM agent, API.
- `database/schema.sql` — MySQL schema (optional; tables are created automatically).
- `backend/local-secrets.example.yml` — template for your own API key.
- No ZIP is needed; everything is in this repository.
