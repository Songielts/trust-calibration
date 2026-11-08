package com.trustcal.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks a model reply against the checkable success criteria of {@link PromptSpec}.
 * A reply that fails any criterion is rejected and never shown to a participant.
 * Note that passing these checks says nothing about whether the answer is TRUE:
 * correctness is decided separately by comparing the answer with the answer key.
 */
public final class AdviceParser {

    public static final int MIN_STEPS = 2;
    public static final int MAX_STEPS = 5;
    public static final int MAX_WORDS_PER_STEP = 50;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record ParsedAdvice(int answerIndex, List<String> reasoningSteps, int confidence) {
    }

    public static class InvalidOutputException extends Exception {
        public InvalidOutputException(String message) {
            super(message);
        }
    }

    private AdviceParser() {
    }

    public static ParsedAdvice parse(String raw, int optionCount) throws InvalidOutputException {
        if (raw == null || raw.isBlank()) {
            throw new InvalidOutputException("Empty reply");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(stripCodeFence(raw));
        } catch (Exception e) {
            throw new InvalidOutputException("Reply is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidOutputException("Reply is not a JSON object");
        }

        // answer: one of the option letters
        JsonNode answer = root.get("answer");
        if (answer == null || !answer.isTextual()) {
            throw new InvalidOutputException("Missing \"answer\" letter");
        }
        String letter = answer.asText().trim().toUpperCase(Locale.ROOT);
        if (letter.endsWith(".") || letter.endsWith(")")) letter = letter.substring(0, letter.length() - 1);
        int index = letter.length() == 1 ? PromptSpec.LETTERS.indexOf(letter.charAt(0)) : -1;
        if (index < 0 || index >= optionCount) {
            throw new InvalidOutputException("\"answer\" is not one of the option letters: " + answer.asText());
        }

        // reasoning_steps: 2-5 short, non-empty strings
        JsonNode steps = root.get("reasoning_steps");
        if (steps == null || !steps.isArray()) {
            throw new InvalidOutputException("Missing \"reasoning_steps\" list");
        }
        List<String> out = new ArrayList<>();
        for (JsonNode s : steps) {
            if (!s.isTextual() || s.asText().isBlank()) {
                throw new InvalidOutputException("A reasoning step is empty or not text");
            }
            String text = s.asText().trim();
            if (text.split("\\s+").length > MAX_WORDS_PER_STEP) {
                throw new InvalidOutputException("A reasoning step is longer than " + MAX_WORDS_PER_STEP + " words");
            }
            out.add(text);
        }
        if (out.size() < MIN_STEPS || out.size() > MAX_STEPS) {
            throw new InvalidOutputException("Expected " + MIN_STEPS + "-" + MAX_STEPS + " reasoning steps, got " + out.size());
        }

        // confidence: integer 0-100 (a self-report, logged but never displayed as a capability signal)
        JsonNode conf = root.get("confidence");
        if (conf == null || !conf.isNumber() || conf.asDouble() != Math.rint(conf.asDouble())
                || conf.asInt() < 0 || conf.asInt() > 100) {
            throw new InvalidOutputException("\"confidence\" must be an integer from 0 to 100");
        }
        return new ParsedAdvice(index, out, conf.asInt());
    }

    /** Some models wrap JSON in ```json fences even when asked not to. */
    static String stripCodeFence(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return s.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return s;
    }
}
