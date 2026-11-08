package com.trustcal.llm;

import java.util.List;

/** A single model call: messages in, the text of the reply out. Implemented over HTTP; faked in tests. */
public interface ChatClient {

    record Message(String role, String content) {
    }

    /**
     * @param jsonMode ask the provider to return a JSON object (OpenAI-compatible response_format)
     * @return the content of the first choice
     */
    String complete(List<Message> messages, double temperature, boolean jsonMode) throws LlmException;
}
