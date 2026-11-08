# Study design and technical notes

Detailed notes behind the README: the experiment, the measures, the LLM agent and the API.

## 1. Experimental design

**2 × 2 between-subjects design.** Each participant is assigned to the condition with the fewest participants so far (balanced assignment, ties broken at random).

| Condition | Capability signal | Step-by-step reasoning |
|---|---|---|
| `CONTROL` | – | – |
| `CAPABILITY` | ✔ | – |
| `REASONING` | – | ✔ |
| `CAPABILITY_REASONING` | ✔ | ✔ |

* **Capability signal** – the agent's *historical accuracy* in the question's topic (e.g. "92% · based on 1,240 questions") and a benchmark score. This is the cue the review identifies as missing from prior work (Section "Gap or opportunity").
* **Step-by-step reasoning** – the agent's numbered reasoning, the cue studied by Bansal et al. (2021) and Möhle et al. (2026), whose findings diverge.

**Two-stage (judge–advisor) trials.** For each of 16 questions the participant (1) gives their own answer and confidence, (2) sees the AI agent's advice with the condition's cues, then (3) gives a final answer, confidence and a per-question trust rating. The initial answer is locked server-side before the advice is released, so reliance can be measured precisely. No correctness feedback is given until the debrief.

**Controlled agent reliability (scripted mode).** The agent's answers are fixed, so its real accuracy is known: 12/16 = 75% overall, and it differs by topic:

| Topic | Advertised accuracy | Actual accuracy in study |
|---|---|---|
| Shopping & Pricing | 92% | 100% (4/4) |
| Everyday Science | 77% | 75% (3/4) |
| Health & Nutrition | 54% | 50% (2/4) |
| Logical Reasoning | 74% | 75% (3/4) |

The agent's wrong answers come with plausible but flawed reasoning, so reasoning alone cannot reveal reliability. Task order is randomised per participant.

**Post-task questionnaire.** 5-item trust scale (adapted from Jian, Bisantz & Drury, 2000; one reverse-scored item), estimates of the agent's accuracy overall and per topic, a comprehension check of the capability signal (capability conditions only — addresses the review's concern that non-experts may misinterpret capability metrics), perceived usefulness of the shown information, and open comments.

**Debrief.** Participants are told how the agent worked (simulated, or which LLM) and can review every question with the correct answer and an explanation.

## 2. Measures computed by the dashboard

| Measure | Definition |
|---|---|
| Over-reliance | P(final = AI advice \| AI wrong) |
| Under-reliance | P(final ≠ AI advice \| AI correct) |
| RAIR (relative AI reliance) | P(switch to AI \| initial wrong, AI correct) |
| RSR (relative self-reliance) | P(keep own answer \| initial correct, AI wrong) |
| Appropriate reliance | Share of decisions that follow correct advice and reject wrong advice |
| Trust discrimination | Mean trust when AI correct − mean trust when AI wrong |
| Trust–correctness r | Point-biserial correlation of per-question trust with AI correctness |
| Calibration error (overall) | \|estimated accuracy − accuracy of the advice this participant actually saw\| (percentage points) |
| Calibration error (per topic) | Mean over topics of \|estimated − accuracy experienced in that topic\| |

Results are also broken down **by topic** (does trust track each topic's real accuracy?) and **over time** (first vs second half of the sequence), the latter addressing the review's point that prior studies miss how reliance evolves. Both trials and participants can be exported as CSV for statistical analysis (e.g. two-way ANOVA on participant-level scores, or mixed-effects logistic regression on decisions).

## 3. The LLM agent (DeepSeek)

The agent can run in three modes, chosen on the dashboard and **fixed per participant at registration** (stored as `agent_mode`), so data from different modes are never mixed silently:

| Mode | Where the advice comes from | Capability signal | Use |
|---|---|---|---|
| `SCRIPTED` | Researcher-written answers (12/16 correct) | Advertised figures (study material, fictional benchmarks) | Controlled experiment |
| `LLM_FROZEN` | Model answers from an evaluation run, majority answer per task, reviewed by the researcher | **Measured** by this system, with date and number of test answers | Real model, same advice for everyone |
| `LLM_LIVE` | A new model call when the participant commits their answer | Measured (as above) | Demonstrating a live agent |

**Model call.** `PromptSpec` holds the versioned specification in six parts (task, audience, constraints, one worked example, success criteria, output format). Only the question and options are sent — never the participant's answer (avoids sycophancy) and never personal data. The provider is asked for JSON output.

**The model proposes, the system disposes.** `AdviceParser` checks every reply against the checkable success criteria (JSON object, valid option letter, 2–5 steps of ≤ 50 words, integer confidence). A reply that fails is logged and never shown. Correctness is decided by the system against the answer key; the model's self-reported confidence is logged but never used as the capability signal.

**Fallbacks.** Live call fails or is invalid → the frozen answer for that task; no valid frozen answer → the scripted answer. Every trial records `advice_source` (`LLM_LIVE`, `LLM_FROZEN`, `FALLBACK_FROZEN`, `FALLBACK_SCRIPTED`, `SCRIPTED`) and the id of the logged call.

**Evaluation loop (prompt log).** "Run evaluation" sends all 16 tasks N times with the same prompt version and temperature, scores every reply and stores per-topic accuracy. The run table on the dashboard is the prompt log (version, settings, validity, accuracy). Freezing a run makes its measured accuracy the capability signal for new LLM-mode participants.

## 4. Demo mode and demo data

**Previewing a condition (for presentations).** Normal participants are assigned to a condition automatically and never see which one. To show a specific interface, open a demo link:

* `/?demo=CONTROL`, `/?demo=CAPABILITY`, `/?demo=REASONING`, `/?demo=CAPABILITY_REASONING`
* `/?demo=off` returns to normal assignment

The same four links are available as buttons at the top of the dashboard. A banner shows the active condition, and demo sessions are stored with `demo = true`: they do not affect balanced assignment and are excluded from all metrics and exports unless "Include demo & simulated data" is switched on. They can be deleted from the Participants tab. Set `DEMO_MODE=false` when collecting real data.

**Simulated participants.** In the Participants tab, **Generate** creates synthetic participants so the dashboard can be previewed before data collection. They are **produced from assumptions coded in `SimulationService`, not findings**; they are flagged (`simulated = true`), excluded by default, and can be deleted with one click.

## 5. REST API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Health check |
| GET | `/api/study/info` | Number of tasks / estimated duration / demo mode |
| GET | `/api/domains` | Topic names (for the questionnaire) |
| POST | `/api/participants` | Register (demographics + consent, optional `demoCondition`) → condition assignment |
| GET | `/api/participants/{pid}` | Session progress |
| GET | `/api/participants/{pid}/trials/{i}` | Question *i* (advice only after stage 1) |
| POST | `/api/participants/{pid}/trials/{i}/initial` | Stage 1 answer → returns the AI advice |
| POST | `/api/participants/{pid}/trials/{i}/final` | Stage 2 answer, confidence, trust |
| POST | `/api/participants/{pid}/survey` | Questionnaire → debrief |
| GET | `/api/participants/{pid}/debrief` | Results and explanations |
| POST | `/api/admin/login` | Check admin token |
| GET | `/api/admin/report?includeSimulated=&agentMode=` | All metrics (filter by agent mode) |
| GET | `/api/admin/participants` · `/tasks` · `/domains` | Lists |
| GET | `/api/admin/export/trials.csv` · `/participants.csv` | CSV export |
| POST / DELETE | `/api/admin/simulate?count=` · `/api/admin/simulated` | Simulated data |
| DELETE | `/api/admin/demo` | Delete demo sessions |
| GET / PUT | `/api/admin/llm/status` · `/api/admin/llm/mode` | LLM status, prompt spec; switch agent mode |
| POST | `/api/admin/llm/test?taskId=` | One test call (shows messages sent, raw output, validation) |
| POST / GET | `/api/admin/llm/runs?repetitions=` · `/api/admin/llm/runs[/{id}]` | Start / list / inspect evaluation runs |
| POST | `/api/admin/llm/runs/{id}/freeze` | Freeze majority answers, make run active |
| GET / PUT | `/api/admin/llm/frozen[/{taskId}]` | Frozen answers; replace one with another valid answer |
| GET | `/api/admin/export/llm_calls.csv` | Every logged model call |

Admin endpoints require the header `X-Admin-Token`.

## 6. Limitations (mirroring the review)
* The tasks are low-stakes, one-session lab tasks; findings may not transfer to repeated, real-world workflows.
* In scripted mode the capability figures and benchmark names are invented study materials. In LLM modes they are measured, but only on 16 questions, so they are coarse; in deployment such signals can also be outdated, manipulated or mismatched to the task.
* A strong model may answer almost every task correctly, leaving few AI errors to measure over-reliance on; the scripted mode exists for this reason.
* The comprehension check measures, but does not fix, misinterpretation of capability metrics by non-experts.
* Before running with real participants, obtain ethics approval as required by your institution and adapt the information sheet accordingly.

## References
* Bansal et al. (2021). Does the whole exceed its parts? CHI 2021.
* Bo, Wan & Anderson (2025). To rely or not to rely? Evaluating appropriate reliance interventions for LLM advice.
* Buçinca, Malaya & Gajos (2021). To trust or to think. PACM HCI 5(CSCW1).
* Cao, Liu & Huang (2024). The role of AI uncertainty presentation, initial user confidence, and task type in fostering appropriate reliance. ACM TOCHI.
* Jian, Bisantz & Drury (2000). Foundations for an empirically determined scale of trust in automated systems. Int. J. Cognitive Ergonomics 4(1).
* Lee & See (2004). Trust in automation: Designing for appropriate reliance. Human Factors 46(1).
* Möhle et al. (2026). "Trust me, Bro!" On the influence of agentic support on trust calibration in AI-assisted decision making. HCII 2026.
* Schemmer et al. (2023). Appropriate reliance on AI advice: Conceptualization and the effect of explanations. IUI 2023.
