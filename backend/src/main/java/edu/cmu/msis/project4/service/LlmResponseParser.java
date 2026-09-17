package edu.cmu.msis.project4.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cmu.msis.project4.model.JobRecommendation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure, defensive parser for the semantic JSON returned by an OpenAI-compatible model. */
public class LlmResponseParser {
    public List<Result> parse(String rawJson, List<JobRecommendation> jobs) {
        String json = stripFences(rawJson);
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("matches")
                || !parsed.getAsJsonObject().get("matches").isJsonArray()) {
            throw new IllegalArgumentException("LLM response must contain a matches array");
        }

        Set<String> knownKeys = new HashSet<>();
        if (jobs != null) {
            for (JobRecommendation job : jobs) {
                if (job != null && job.jobKey != null && !job.jobKey.isBlank()) {
                    knownKeys.add(job.jobKey);
                }
            }
        }

        List<Result> results = new ArrayList<>();
        JsonArray matches = parsed.getAsJsonObject().getAsJsonArray("matches");
        for (JsonElement element : matches) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject match = element.getAsJsonObject();
            String jobKey = string(match, "jobKey");
            if (jobKey.isBlank() || !knownKeys.contains(jobKey)) {
                continue;
            }
            int score = integer(match, "semanticScore", integer(match, "matchScore", 0));
            results.add(new Result(
                    jobKey,
                    clamp(score, 0, 100),
                    truncate(string(match, "rationale"), 500),
                    strings(match, "transferableSkills"),
                    strings(match, "missingSkills"),
                    truncate(string(match, "currentBullet"), 700),
                    truncate(string(match, "suggestedBullet"), 700),
                    truncate(string(match, "resumeAdvice"), 600),
                    clampDouble(number(match, "confidence", 0.0), 0.0, 1.0)));
        }
        if (results.isEmpty()) {
            throw new IllegalArgumentException("LLM response did not contain a valid matching job");
        }
        return results;
    }

    private String stripFences(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("LLM response is empty");
        }
        return rawJson.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
    }

    private String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private double number(JsonObject object, String key, double fallback) {
        try {
            return object.has(key) ? object.get(key).getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private List<String> strings(JsonObject object, String key) {
        List<String> values = new ArrayList<>();
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return values;
        }
        for (JsonElement element : object.getAsJsonArray(key)) {
            if (!element.isJsonNull()) {
                String value = element.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(truncate(value, 160));
                }
            }
        }
        return values;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "…";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Result {
        public final String jobKey;
        public final int semanticScore;
        public final String rationale;
        public final List<String> transferableSkills;
        public final List<String> missingSkills;
        public final String currentBullet;
        public final String suggestedBullet;
        public final String resumeAdvice;
        public final double confidence;

        public Result(
                String jobKey,
                int semanticScore,
                String rationale,
                List<String> transferableSkills,
                List<String> missingSkills,
                String currentBullet,
                String suggestedBullet,
                String resumeAdvice,
                double confidence) {
            this.jobKey = jobKey;
            this.semanticScore = semanticScore;
            this.rationale = rationale;
            this.transferableSkills = List.copyOf(transferableSkills == null ? List.of() : transferableSkills);
            this.missingSkills = List.copyOf(missingSkills == null ? List.of() : missingSkills);
            this.currentBullet = currentBullet;
            this.suggestedBullet = suggestedBullet;
            this.resumeAdvice = resumeAdvice;
            this.confidence = confidence;
        }
    }
}
