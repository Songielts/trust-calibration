-- =====================================================================
--  Trust Calibration Study Platform - MySQL 8 schema
--  "Showing Strength to Earn Trust? Capability Signals and Trust
--   Calibration in Human-AI Agent Interaction"
--
--  Running this script is optional: Spring Boot (ddl-auto=update) creates
--  the same tables automatically, and DataSeeder inserts the 4 topics and
--  16 decision tasks on first start-up when the tables are empty.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS trust_calibration
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE trust_calibration;

-- Task domains and the capability signal advertised for each
CREATE TABLE IF NOT EXISTS domain_profile (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  code                VARCHAR(32)  NOT NULL,
  name                VARCHAR(100) NOT NULL,
  description         VARCHAR(500),
  historical_accuracy DOUBLE       NOT NULL COMMENT 'advertised accuracy, 0-100',
  sample_size         INT          NOT NULL COMMENT 'number of past questions behind the accuracy figure',
  benchmark_name      VARCHAR(100),
  benchmark_score     DOUBLE,
  display_order       INT,
  PRIMARY KEY (id),
  UNIQUE KEY uk_domain_code (code)
) ENGINE = InnoDB;

-- Multiple-choice decision tasks with the (simulated) agent's fixed answer
CREATE TABLE IF NOT EXISTS decision_task (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  code            VARCHAR(32) NOT NULL,
  domain_code     VARCHAR(32) NOT NULL,
  prompt          TEXT        NOT NULL,
  options_json    TEXT        NOT NULL COMMENT 'JSON array of answer options',
  correct_index   INT         NOT NULL,
  ai_answer_index INT         NOT NULL,
  reasoning_json  TEXT        NOT NULL COMMENT 'JSON array of reasoning steps',
  explanation     TEXT,
  display_order   INT,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_code (code)
) ENGINE = InnoDB;

-- Participants (anonymous) and their experimental condition
CREATE TABLE IF NOT EXISTS participant (
  id                   BIGINT      NOT NULL AUTO_INCREMENT,
  public_id            VARCHAR(36) NOT NULL,
  experiment_condition VARCHAR(32) NOT NULL COMMENT 'CONTROL | CAPABILITY | REASONING | CAPABILITY_REASONING',
  age_group            VARCHAR(20),
  gender               VARCHAR(30),
  education            VARCHAR(50),
  ai_familiarity       INT COMMENT '1-7',
  ai_usage_frequency   VARCHAR(30),
  trust_propensity     INT COMMENT '1-7',
  task_order           VARCHAR(500) NOT NULL COMMENT 'randomised task ids, comma separated',
  status               VARCHAR(20)  NOT NULL COMMENT 'IN_PROGRESS | COMPLETED',
  simulated            BIT(1)       NOT NULL DEFAULT b'0',
  demo                 BIT(1)       NOT NULL DEFAULT b'0' COMMENT 'session started from a demo link',
  agent_mode           VARCHAR(20)  DEFAULT 'SCRIPTED' COMMENT 'SCRIPTED | LLM_FROZEN | LLM_LIVE',
  evaluation_run_id    BIGINT       COMMENT 'LLM modes: run that supplied capability signal and frozen answers',
  created_at           DATETIME(6)  NOT NULL,
  completed_at         DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_participant_public_id (public_id)
) ENGINE = InnoDB;

-- One row per decision (two-stage judge-advisor trial)
CREATE TABLE IF NOT EXISTS trial (
  id                 BIGINT NOT NULL AUTO_INCREMENT,
  participant_id     BIGINT NOT NULL,
  task_id            BIGINT NOT NULL,
  trial_index        INT    NOT NULL,
  initial_answer     INT    NOT NULL,
  initial_confidence INT    NOT NULL COMMENT '0-100',
  initial_time_ms    BIGINT,
  final_answer       INT,
  final_confidence   INT COMMENT '0-100',
  trust_rating       INT COMMENT '1-7, trust in the advice on this question',
  final_time_ms      BIGINT,
  ai_answer          INT COMMENT 'option the agent recommended on this trial',
  advice_source      VARCHAR(20) COMMENT 'SCRIPTED | LLM_FROZEN | LLM_LIVE | FALLBACK_FROZEN | FALLBACK_SCRIPTED',
  llm_advice_id      BIGINT COMMENT 'logged LLM call that produced the advice',
  ai_correct         BIT(1) NOT NULL,
  initial_correct    BIT(1) NOT NULL,
  final_correct      BIT(1),
  initial_agree      BIT(1) NOT NULL,
  final_agree        BIT(1),
  changed_answer     BIT(1),
  completed          BIT(1) NOT NULL DEFAULT b'0',
  created_at         DATETIME(6) NOT NULL,
  completed_at       DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_trial_participant_task (participant_id, task_id),
  CONSTRAINT fk_trial_participant FOREIGN KEY (participant_id) REFERENCES participant (id),
  CONSTRAINT fk_trial_task FOREIGN KEY (task_id) REFERENCES decision_task (id)
) ENGINE = InnoDB;

-- Post-task questionnaire
CREATE TABLE IF NOT EXISTS post_survey (
  id                         BIGINT NOT NULL AUTO_INCREMENT,
  participant_id             BIGINT NOT NULL,
  trust_confident            INT    NOT NULL,
  trust_reliable             INT    NOT NULL,
  trust_trustworthy          INT    NOT NULL,
  trust_dependable           INT    NOT NULL,
  trust_wary                 INT    NOT NULL COMMENT 'reverse-scored',
  estimated_overall_accuracy INT    NOT NULL COMMENT '0-100',
  estimated_domain_accuracy  TEXT COMMENT 'JSON object: domain code -> estimate',
  info_usefulness            INT,
  comprehension_answer       VARCHAR(10),
  comprehension_correct      BIT(1),
  comments                   TEXT,
  created_at                 DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_survey_participant (participant_id),
  CONSTRAINT fk_survey_participant FOREIGN KEY (participant_id) REFERENCES participant (id)
) ENGINE = InnoDB;

-- Run-time settings chosen on the dashboard (agent mode, active evaluation run)
CREATE TABLE IF NOT EXISTS study_setting (
  setting_key   VARCHAR(64)  NOT NULL,
  setting_value VARCHAR(255),
  PRIMARY KEY (setting_key)
) ENGINE = InnoDB;

-- One run of the fixed evaluation loop (doubles as the prompt log)
CREATE TABLE IF NOT EXISTS evaluation_run (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  model           VARCHAR(100) NOT NULL,
  prompt_version  VARCHAR(20)  NOT NULL,
  temperature     DOUBLE       NOT NULL,
  repetitions     INT          NOT NULL,
  total_calls     INT          NOT NULL,
  valid_calls     INT,
  correct_calls   INT,
  domain_accuracy TEXT COMMENT 'JSON: domain code -> measured accuracy %',
  domain_attempts TEXT COMMENT 'JSON: domain code -> number of calls',
  status          VARCHAR(20)  NOT NULL COMMENT 'RUNNING | COMPLETED | FAILED',
  note            VARCHAR(500),
  started_at      DATETIME(6)  NOT NULL,
  finished_at     DATETIME(6),
  PRIMARY KEY (id)
) ENGINE = InnoDB;

-- Every model call (evaluation, live, test), valid or not
CREATE TABLE IF NOT EXISTS llm_advice (
  id                       BIGINT       NOT NULL AUTO_INCREMENT,
  task_id                  BIGINT       NOT NULL,
  run_id                   BIGINT,
  purpose                  VARCHAR(20)  NOT NULL COMMENT 'EVALUATION | LIVE | TEST',
  model                    VARCHAR(100) NOT NULL,
  prompt_version           VARCHAR(20)  NOT NULL,
  valid                    BIT(1)       NOT NULL,
  validation_error         VARCHAR(500),
  answer_index             INT,
  reasoning_json           TEXT,
  self_reported_confidence INT COMMENT 'logged only, never shown as a capability signal',
  correct                  BIT(1)       NOT NULL COMMENT 'checked by the system against the answer key',
  raw_output               TEXT,
  latency_ms               BIGINT,
  frozen                   BIT(1)       NOT NULL DEFAULT b'0',
  created_at               DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_llm_advice_task FOREIGN KEY (task_id) REFERENCES decision_task (id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Useful analysis queries
-- ---------------------------------------------------------------------
-- Over-reliance per condition (share of wrong AI advice that was followed):
-- SELECT p.experiment_condition, AVG(t.final_agree) AS over_reliance
--   FROM trial t JOIN participant p ON p.id = t.participant_id
--  WHERE t.completed = 1 AND t.ai_correct = 0 AND p.status = 'COMPLETED' AND p.simulated = 0 AND p.demo = 0
--  GROUP BY p.experiment_condition;
--
-- Mean trust rating when the AI was right vs wrong, per condition:
-- SELECT p.experiment_condition, t.ai_correct, AVG(t.trust_rating) AS mean_trust
--   FROM trial t JOIN participant p ON p.id = t.participant_id
--  WHERE t.completed = 1 AND p.status = 'COMPLETED' AND p.simulated = 0 AND p.demo = 0
--  GROUP BY p.experiment_condition, t.ai_correct;
