package com.trustcal.entity;

/** What actually produced the advice shown on one trial (recorded per trial). */
public enum AdviceSource {
    SCRIPTED,
    LLM_FROZEN,
    LLM_LIVE,
    /** Live call failed or was invalid; the frozen LLM answer for this task was shown instead. */
    FALLBACK_FROZEN,
    /** No usable LLM answer was available; the scripted answer was shown instead. */
    FALLBACK_SCRIPTED
}
