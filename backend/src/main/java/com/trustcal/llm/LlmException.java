package com.trustcal.llm;

/** The LLM call itself failed (network, HTTP error, timeout, empty reply). */
public class LlmException extends Exception {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
