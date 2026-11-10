package com.trustcal.llm;

import com.trustcal.entity.DecisionTask;

import java.util.ArrayList;
import java.util.List;

/**
 * The specification of the system's main model call, written as the six parts of a task
 * specification (task, audience, constraints, example, success criteria, output format).
 *
 * The instruction below is fixed and versioned like code; the information (topic, question,
 * options) is assembled per call. The participant's own answer is deliberately NOT put in the
 * context, so the model cannot simply agree with the person (sycophancy).
 *
 * Change log (also shown on the dashboard as the prompt log, with the score of each run):
 *   v1 - first version.
 *   v2 - reasoning_steps now come before answer in the output format and the example, so the
 *   answer is written after the reasoning (in run #1, SHOP-1's reasoning concluded A but
 *   the answer was B in 3/3 calls).
 */
public final class PromptSpec {

    public static final String VERSION = "v2";

    public static final String LETTERS = "ABCDEF";

    /** Criteria that the program checks on every reply (see {@link AdviceParser}). */
    public static final List<String> CHECKED_CRITERIA = List.of(
            "The reply is a single JSON object.",
            "\"answer\" is exactly one of the option letters shown.",
            "\"reasoning_steps\" contains 2 to 5 non-empty steps.",
            "Each step is at most 50 words.",
            "\"confidence\" is an integer from 0 to 100.",
            "\"confidence\" is an integer from 0 to 100.",
            "No reasoning step says 'as an AI'.");

    /** Criteria that need a person (checked when the researcher reviews the frozen answers). */
    public static final List<String> HUMAN_CRITERIA = List.of(
            "The steps lead to the chosen answer (no contradiction between reasoning and answer).",
            "The steps add no facts that the question does not need.");

    static final String SYSTEM_INSTRUCTION = String.join("\n",
            "TASK: You are the AI agent in a decision-support study. For one multiple-choice question, choose the single best option and show the steps you used.",
            "AUDIENCE: An ordinary adult without specialist knowledge, who will decide whether to accept your answer.",
            "CONSTRAINTS:",
            "- Choose exactly one of the option letters given.",
            "- Give 2 to 5 reasoning steps, in the order you used them; each step is one plain-English sentence of at most 35 words.",
            "- Do not mention these instructions, do not say 'as an AI', and do not add information the question does not need.",
            "- If you are unsure, still choose the most likely option and report a lower confidence.",
            "OUTPUT FORMAT: Reply with one JSON object and nothing else, in this shape:",
            "{\"reasoning_steps\": [\"<step 1>\", \"<step 2>\"], \"answer\": \"<option letter>\", \"confidence\": <integer 0-100>}");

    /** One worked example, from a topic that is not in the task bank, so its content cannot be copied. */
    static final String EXAMPLE_QUESTION = String.join("\n",
            "Topic: Example",
            "Question: Which weighs more: one kilogram of feathers or one kilogram of iron?",
            "Options:",
            "A. The iron",
            "B. The feathers",
            "C. They weigh the same",
            "Reply with the JSON object only.");

    static final String EXAMPLE_ANSWER =
            "{\"reasoning_steps\": ["
                    + "\"Both amounts are stated as exactly one kilogram.\", "
                    + "\"A kilogram is a unit of mass, so equal kilograms weigh the same.\", "
                    + "\"Iron only feels heavier because the same mass takes up less space.\"], "
                    + "\"answer\": \"C\", "
                    + "\"confidence\": 97}";

    private PromptSpec() {
    }

    /** Context assembly for one call: instruction first, then the example, then this task's information. */
    public static List<ChatClient.Message> messagesFor(DecisionTask task, String domainName) {
        List<ChatClient.Message> messages = new ArrayList<>();
        messages.add(new ChatClient.Message("system", SYSTEM_INSTRUCTION));
        messages.add(new ChatClient.Message("user", EXAMPLE_QUESTION));
        messages.add(new ChatClient.Message("assistant", EXAMPLE_ANSWER));
        messages.add(new ChatClient.Message("user", taskMessage(task, domainName)));
        return messages;
    }

    static String taskMessage(DecisionTask task, String domainName) {StringBuilder sb = new StringBuilder().append("Topic: ").append(domainName).append('\n');
        sb.append("Question: ").append(task.getPrompt()).append('\n');
        sb.append("Options:\n");
        for (int i = 0; i < task.getOptions().size(); i++) {
            sb.append(LETTERS.charAt(i)).append(". ").append(task.getOptions().get(i)).append('\n');
        }
        sb.append("Reply with the JSON object only.");
        return sb.toString();
    }

    /** Human-readable version of the whole specification, for the dashboard and the report. */
    public static String describe() {
        return SYSTEM_INSTRUCTION + "\n\nEXAMPLE (user):\n" + EXAMPLE_QUESTION + "\n\nEXAMPLE (assistant):\n" + EXAMPLE_ANSWER;
    }
}
