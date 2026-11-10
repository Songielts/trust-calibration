# Trust Calibration Study Platform

*README prose: about 1,450 words (code and commands not counted).*

A web system for studying whether showing an AI agent's **capability** (its accuracy in a topic), alone or with its **step-by-step reasoning**, helps people trust the agent as much as it deserves — no more, no less. People answer 16 everyday questions, see the agent's advice, and decide whether to follow it; the system records their reliance and trust and computes trust-calibration measures for a researcher dashboard. It implements the research question from my Assignment 1 review.

Detailed design notes, measures and API: [`docs/study-design.md`](docs/study-design.md).

## Project status

| Part | Status |
|---|---|
| Participant flow: consent → background questions → 16 two-stage questions (own answer first, then advice) → questionnaire → debrief | Implemented |
| 2 × 2 conditions (capability signal × reasoning), balanced assignment, demo links to preview a condition | Implemented |
| Scripted agent: answers written in advance, 12/16 correct, accuracy differs by topic | Implemented — the agent is **simulated** in this mode |
| Capability figures in scripted mode (e.g. "92%", "RetailQA-Bench") | **Simulated** study material; the benchmark names are invented |
| LLM agent (DeepSeek, any OpenAI-compatible API): live and pre-generated modes, JSON output validation, fallbacks | Implemented; tested with a fake model, a local mock server and the real DeepSeek API (`deepseek-chat`, 8 Nov 2026) |
| Evaluation runs that **measure** the LLM's accuracy per topic and use it as the capability signal | Implemented; two real runs (16 tasks × 3 calls, prompt v1 and v2), every frozen answer reviewed by hand — see *LLM evaluation* below |
| Researcher dashboard: reliance and calibration measures by condition, topic and time; CSV export | Implemented |
| Synthetic participants for previewing the dashboard | Implemented; output is **simulated** data, never findings |
| Pilot sessions with real people | Planned |
| Repeated use over several weeks; multi-step agent tasks; harder questions so the LLM makes more errors | Planned — the evaluation runs show why harder questions are needed |

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
1. Copy `backend/local-secrets.example.yml` to `backend/local-secrets.yml` (git-ignored) and put your MySQL password there under `spring.datasource.password`, or set `DB_PASSWORD`. The database and the 16 tasks are created automatically.
2. Backend: `cd backend` → `mvn spring-boot:run` (check http://localhost:8080/api/health). No MySQL? add `-Dspring-boot.run.profiles=h2`.
3. Frontend: `cd frontend` → `npm install` → `npm run dev`, then open http://localhost:5173 (dashboard: `/admin`).

**LLM agent (optional).** Without a key everything runs with the scripted agent. To use DeepSeek, provide **your own** key — never commit it:
- paste it into `backend/local-secrets.yml` under `llm.api-key`, **or**
- set the environment variable `LLM_API_KEY` (for Docker: a `.env` file next to `docker-compose.yml`, also git-ignored).

Then on the dashboard → **AI agent**: send a test call → run an evaluation → freeze it → choose the agent mode.

**Preview a condition:** `/?demo=CAPABILITY_REASONING` (also `CONTROL`, `CAPABILITY`, `REASONING`; `/?demo=off` to stop). Demo sessions are excluded from the analysis.

**Tests:** `cd backend` → `mvn test` (25 tests, no database or API key needed).

## LLM evaluation

Each run sends all 16 tasks to the model three times with the same prompt and temperature (0.7). The system scores every reply against the answer key. The model's self-reported confidence is logged but never used as a check. Every call is in [`docs/evaluation/llm_calls_2026-11-08.csv`](docs/evaluation/llm_calls_2026-11-08.csv).

| Run | Prompt | Valid | Correct | Shopping | Science | Health | Logic |
|---|---|---|---|---|---|---|---|
| #1 | v1 | 48/48 | 45/48 (94%) | 9/12 (75%) | 12/12 | 12/12 | 12/12 |
| #2 | v2 | 48/48 | 48/48 (100%) | 12/12 | 12/12 | 12/12 | 12/12 |

**Human review of run #1.** I read all 16 frozen answers against the two human criteria (the steps lead to the answer; no unneeded facts).
- **SHOP-1:** in all three calls the reasoning concluded that Pack A was cheaper, yet the answer was B. Every reply passed the automatic checks, so only a human reader could see the contradiction.
- **SHOP-3:** the answer was right (A) in all three calls, but two of the three reasoning chains left out the £60 upfront cost. The default frozen reply (call 9) was one of these, so I replaced it with call 24, which gives the same answer with correct reasoning.

**Prompt v2.** The v1 output format asked for `answer` before `reasoning_steps`, so the model committed to an answer before writing any reasoning. v2 changes only that order (in the format and in the example). In run #2 both problems disappeared. With three calls per task this supports the explanation but does not prove it.

**What this means for the study.** With v2 the model answered every question correctly. In LLM mode participants would therefore never meet wrong advice, so over-reliance could not be measured and the capability signal would read 100% for every topic. For this question set the scripted agent, whose errors are designed in advance, remains the main experimental condition.

## Development log

| When | Change | Why | Checked |
|---|---|---|---|
| Oct (A1) | Literature review and research question | Prior work tests explanations and uncertainty, but not capability signals | — |
| 29 Oct | **v1**: scripted 2 × 2 study platform, two-stage trials, trust questionnaire, dashboard | A controlled agent is needed to know when trust *should* be high or low | In-memory test of the full flow and every measure against hand-computed values |
| 29 Oct | Compared v1 with a second, simpler prototype (TrustLens) | To choose a base | Kept v1: TrustLens had no trust measures, participants chose their own condition, and its 3 tasks had a capability figure unrelated to real accuracy |
| 29 Oct | **v2**: demo links, Docker, JUnit tests, pinned versions | Taken from the comparison: a condition must be easy to show without corrupting the data | 11 JUnit tests |
| 1 Nov | **v3**: real LLM agent (DeepSeek) alongside the scripted agent; measured capability; prompt log | Weeks 3–4: reliability is a property of the system, so the capability signal should be measured, not claimed; the scripted mode stays for experimental control | 13 more tests with a fake model; HTTP client checked against a local mock server |
| 4–5 Nov | First local run on Windows (IntelliJ IDEA, JDK 17, MySQL 8 with Navicat, Node/Vite) | To run and study the system myself | Backend, frontend and database started; completed a full participant session and checked the stored rows in MySQL |
| 5 Nov | Start-up log line in `DataSeeder`; practice derived-query methods in `ParticipantRepository` | To see when seeding runs, and to learn how Spring Data turns method names into SQL | Log shows `Seeder running, domains = 4`; the backend starts with the new query methods |
| 5–6 Nov | Code walk-through with the debugger and the SQL log | To understand balanced assignment, the question-order rule and transactions | Stepped through `nextBalancedCondition` (the counts matched MySQL); confirmed that skipping a question is refused by the server, not only by the page; a forced exception in `submitFinal` rolled back the update (test code removed afterwards) |
| 8 Nov | First real DeepSeek calls and evaluation run #1 (prompt v1) | Confirm the LLM path with the real API and measure accuracy per topic | 48/48 valid, 45/48 correct; all 16 frozen answers reviewed by hand (SHOP-1 reasoning–answer contradiction; SHOP-3 faulty reasoning, frozen reply replaced) |
| 8 Nov | **Prompt v2**: `reasoning_steps` before `answer`; evaluation run #2 | In v1 the model chose an answer before reasoning (SHOP-1) | 48/48 valid, 48/48 correct; SHOP-1 and SHOP-3 reasoning now match the answers (3 calls per task, so a small test) |
| 9 Nov | `AdviceParser` rejects reasoning steps that say "as an AI" (+ test) | The prompt forbids it, but the parser did not check it | Test written first and seen to fail; with the rule, all 25 tests pass. I also recomputed over- and under-reliance for my own session from the exported trials and they matched the dashboard |

Commits: the repository was created on 8 Nov. v1–v3 were built before version control, so the first commit imports v3 together with my first changes; prompt v2 and later work are separate commits in the history.

## AI assistance

I used **Claude (Anthropic, claude.ai)** throughout:
- **Code**: asked it to implement the study from my A1 review with Spring Boot + Vue + MySQL and English UI text; it generated most of the backend and frontend code and tests.
- **Design decisions**: asked it to compare its version with another prototype and to explain the trade-offs; I chose to keep both a scripted and a real LLM agent and to use DeepSeek.
- **Mapping to lectures**: asked how the design relates to the Weeks 1–4 concepts.
- **Learning the code**: used it as a tutor while I worked through the code over two weeks.
- **README and report drafts.**
- Added a validation rule to `AdviceParser` and wrote its test first (it failed, then passed); recomputed reliance measures from the exported CSV by hand.
What I checked or changed myself:
- Ran the whole system locally on Windows and completed participant sessions in each condition.
- Checked the stored data in MySQL and followed the generated SQL with the Hibernate log.
- Stepped through condition assignment, the question-order rule and a transaction rollback in the debugger.
- Added a start-up log line in `DataSeeder` and practice query methods in `ParticipantRepository`.
- Ran two real evaluation runs with DeepSeek and read every frozen answer against the human criteria; I found the SHOP-1 contradiction and the SHOP-3 reasoning error and replaced one frozen reply.
- Diagnosed the cause of the SHOP-1 error and wrote prompt v2 to test it.

## Additional files

- `docs/study-design.md` — experimental design, measures, LLM agent, API.
- `docs/evaluation/llm_calls_2026-11-08.csv` — every model call from 8 Nov (tests and evaluation runs #1–#2), with validity, correctness, frozen flags and reasoning.
- `database/schema.sql` — MySQL schema (optional; tables are created automatically).
- `backend/local-secrets.example.yml` — template for your own API key and database password.
- No ZIP is needed; everything is in this repository.
