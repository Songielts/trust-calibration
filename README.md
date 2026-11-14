# Trust Calibration Study Platform

*README prose: about 1,250 words (code, commands and file paths not counted).*

A web system for studying whether showing an AI agent's **capability signal** (its accuracy in a topic), alone or with its **step-by-step reasoning**, helps people trust the agent as much as it deserves. Participants answer 16 everyday questions, give their own answer first, then see the agent's advice and make a final decision. The system records reliance and trust, and a researcher dashboard computes trust-calibration measures. It implements the research question from my Assignment 1 review; the design rationale is in the project report.

More detail on the design, measures and API: [`docs/study-design.md`](docs/study-design.md).

## Project status

| Part | Status |
|---|---|
| Participant flow: consent → background questions → 16 two-stage questions → questionnaire → debrief | Implemented |
| 2 × 2 conditions (capability signal × reasoning), balanced assignment, demo links to preview each condition | Implemented |
| Scripted agent (answers written in advance, 12/16 correct, accuracy differs by topic) | Implemented; the agent is **simulated** |
| Capability figures in scripted mode (e.g. "92%", "RetailQA-Bench") | **Simulated** study material; benchmark names are invented |
| LLM agent (DeepSeek): live and pre-generated modes, reply validation, fallbacks | Implemented; tested with the real API on 8 Nov 2026 |
| Evaluation runs that measure the LLM's accuracy per topic and use it as the capability signal | Implemented; two real runs, all frozen answers reviewed by hand |
| Researcher dashboard with reliance and calibration measures; CSV export | Implemented |
| Synthetic participants for previewing the dashboard | Implemented; **simulated** data, never findings |
| Pilot sessions with real participants | Planned |
| Harder, less familiar questions so the LLM makes errors; repeated use over several weeks | Planned |

## Technology

| Layer | Technology | Role |
|---|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Data JPA | REST API, experiment rules, analytics |
| Database | MySQL 8 (H2 in-memory profile for quick demos) | Participants, trials, questionnaires, logged model calls |
| Frontend | Vue 3, Vite, Vue Router, Element Plus | Participant pages and researcher dashboard |
| AI model in the system | DeepSeek `deepseek-chat` through its OpenAI-compatible API (configurable) | Generates the agent's answer and reasoning in the LLM modes |
| AI tool for development | Claude (Anthropic), claude.ai | See *AI assistance* |
| Tests and deployment | JUnit 5, Docker Compose, nginx | 25 tests; one-command start |

**Not yet decided**
- Which agent mode the main study will use. The scripted agent is the current choice, because the LLM answered all 16 questions correctly (see below).
- The model name: on 8 Nov DeepSeek's API listed `deepseek-flash` and `deepseek-v4-pro`, and still accepted `deepseek-chat`. It can be changed in `application.yml`.

## Run or inspect

**Quickest (Docker Desktop):**
```bash
docker compose up -d --build
```
Study at http://localhost:8081, dashboard at http://localhost:8081/admin (token `admin123`).

**Local development** (JDK 17+, Maven, Node 18+, MySQL 8):
1. Copy `backend/local-secrets.example.yml` to `backend/local-secrets.yml` (git-ignored) and set your MySQL password there, or set the environment variable `DB_PASSWORD`. The database and the 16 tasks are created on first start.
2. Backend: `cd backend` → `mvn spring-boot:run` (check http://localhost:8080/api/health). Without MySQL, add `-Dspring-boot.run.profiles=h2`.
3. Frontend: `cd frontend` → `npm install` → `npm run dev`, then open http://localhost:5173 (dashboard: `/admin`).

**LLM agent (optional).** Without a key, everything runs with the scripted agent. To use DeepSeek, provide **your own** key; never commit it:
- put it in `backend/local-secrets.yml` under `llm.api-key`, or
- set `LLM_API_KEY` (for Docker, in a git-ignored `.env` file next to `docker-compose.yml`).

Then, on the dashboard's **AI agent** tab: send a test call → run an evaluation → freeze it → choose the agent mode.

**Preview a condition:** `/?demo=CAPABILITY_REASONING` (also `CONTROL`, `CAPABILITY`, `REASONING`; `/?demo=off` to stop). Demo sessions are excluded from the analysis.

**Inspect the data:** the dashboard's **Export CSV** menu gives one row per decision, per participant or per model call.

**Tests:** `cd backend` → `mvn test` (25 tests; no database or API key needed).

## LLM evaluation (summary)

Each run sends all 16 tasks to the model three times with the same prompt, and the system checks every reply against the answer key. Every call is in [`docs/evaluation/llm_calls_2026-11-08.csv`](docs/evaluation/llm_calls_2026-11-08.csv).

| Run | Prompt | Valid | Correct | By topic |
|---|---|---|---|---|
| #1 | v1 | 48/48 | 45/48 | Shopping 9/12, others 12/12 |
| #2 | v2 | 48/48 | 48/48 | all 12/12 |

- **Run #1 review.** In SHOP-1 the reasoning concluded that Pack A was cheaper, but the answer was B, in all three calls; every reply had passed the automatic checks. In SHOP-3 the answer was right, but two of three reasoning chains left out the £60 upfront cost, so I replaced the frozen reply with one that gives the same answer with correct reasoning. I did not edit any of the model's answers.
- **Prompt v2** changes only one thing: the reasoning comes before the answer. Both problems disappeared in run #2 (three calls per task, so a small test).

## Development record

v1–v3 were built before the repository was created on 8 Nov, so the first commit imports v3. Later work is in separate commits.

| Stage | Change | Why | Checked | Commit |
|---|---|---|---|---|
| 29 Oct | **v1**: scripted 2 × 2 platform, two-stage trials, trust questionnaire, dashboard | A controlled agent is needed to know when trust *should* be high or low | Full flow and every measure checked against hand-computed values | before git |
| 29 Oct | Compared v1 with a simpler prototype (TrustLens); **v2** added demo links, Docker and tests | To choose a base and show conditions without corrupting data | Kept v1: TrustLens had no trust measures and its capability figure was unrelated to real accuracy | before git |
| 1 Nov | **v3**: real LLM agent alongside the scripted one; measured capability signal; prompt log | Reliability is a property of the system, so the signal should be measured, not claimed | Tests with a fake model; HTTP client checked against a mock server | [901d9a3](https://github.com/Songielts/trust-calibration/commit/901d9a3) |
| 4–8 Nov | Ran the system on Windows; studied it with the debugger and SQL log; added a seeding log line and practice query methods | To run and understand the system myself | Balanced assignment matched counts in MySQL; question skipping is refused by the server; a forced exception rolled back the update | [901d9a3](https://github.com/Songielts/trust-calibration/commit/901d9a3) |
| 8 Nov | First DeepSeek evaluation (run #1), human review, **prompt v2**, run #2 | Confirm the LLM path and measure accuracy per topic | See *LLM evaluation* | [2416650](https://github.com/Songielts/trust-calibration/commit/2416650), [841e6d2](https://github.com/Songielts/trust-calibration/commit/841e6d2) |
| 11 Nov | `AdviceParser` rejects reasoning steps that say "as an AI" | The prompt forbids it, but nothing checked it | Test written first and seen to fail, then passed | [e567ae9](https://github.com/Songielts/trust-calibration/commit/e567ae9) |
| 11 Nov | Progress dots on the question page | Practise Vue components | Same in all four conditions, so the manipulation is unchanged | [85d4c24](https://github.com/Songielts/trust-calibration/commit/85d4c24) |
| 12 Nov | New dashboard measure: confidence shift (final − initial confidence) | Practise a full backend and frontend change | A first version gave 98.9; recomputing from the CSV gave +7.5 and revealed a wrong field, which I fixed | [9e7c10f](https://github.com/Songielts/trust-calibration/commit/9e7c10f) |
| 14 Nov | Restored environment-variable defaults for the database password and API key in `application.yml` | Placeholder text had replaced them, so `DB_PASSWORD`, `LLM_API_KEY` and the Docker settings were ignored | Backend still starts locally; searched the history and no real key was ever committed | latest |

Full history: [commits on `main`](https://github.com/Songielts/trust-calibration/commits/main).

## AI assistance

**Tool:** Claude (Anthropic, claude.ai).

**What I asked it to do**
- Implement the study from my A1 review with Spring Boot, Vue and MySQL, with English UI text. It generated most of the backend, frontend and tests.
- Compare its version with another prototype and explain the trade-offs. I chose to keep both a scripted and an LLM agent and to use DeepSeek.
- Relate the design to the Weeks 1–4 concepts, act as a tutor while I worked through the code, and draft this README and the report.

**What I checked or changed myself**
- Ran the system locally, completed a full session and tried every condition; checked the stored rows in MySQL.
- Ran two real evaluations and read every frozen answer. I found the SHOP-1 contradiction, worked out its likely cause and tested it with prompt v2.
- Made my own changes (validation rule and test, progress component, confidence-shift measure) and recomputed reliance and confidence measures from the CSV to check the dashboard. This is how I caught the bug in my own measure.
- Corrected AI-drafted text where it did not match what I actually did, for example dates and the scope of my checks.

## Additional files

- `docs/study-design.md`: experimental design, measures, LLM agent and API.
- `docs/evaluation/llm_calls_2026-11-08.csv`: every model call on 8 Nov (tests and runs #1–#2).
- `database/schema.sql`: MySQL schema (optional; tables are created automatically).
- `backend/local-secrets.example.yml`: template for your own API key and database password.
- No ZIP is needed; everything is in this repository.
