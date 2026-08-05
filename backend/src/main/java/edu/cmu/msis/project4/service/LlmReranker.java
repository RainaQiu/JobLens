package edu.cmu.msis.project4.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cmu.msis.project4.config.AppConfig;
import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.model.RecommendationRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional OpenAI-compatible semantic reranker. It works with Qwen Model Studio or Gemini
 * by changing only API key, base URL, and model environment variables.
 */
public class LlmReranker {
    private static final int MAX_CANDIDATES = 20;
    private static final int MAX_RESUME_CHARS = 8_000;
    private static final int MAX_DESCRIPTION_CHARS = 2_500;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final Gson gson = new Gson();

    public List<JobRecommendation> rerank(List<JobRecommendation> candidates, RecommendationRequest request) {
        if (!isEnabled(request) || candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        int candidateCount = Math.min(MAX_CANDIDATES, candidates.size());
        List<JobRecommendation> topCandidates = new ArrayList<>(candidates.subList(0, candidateCount));
        try {
            String content = callModel(topCandidates, request);
            applyModelResults(topCandidates, content);
            topCandidates.sort(Comparator.comparingInt((JobRecommendation job) -> job.matchScore).reversed());

            List<JobRecommendation> merged = new ArrayList<>(topCandidates);
            if (candidates.size() > candidateCount) {
                merged.addAll(candidates.subList(candidateCount, candidates.size()));
            }
            return merged;
        } catch (Exception ignored) {
            // The search path must remain available when an optional model provider is down.
            return candidates;
        }
    }

    private boolean isEnabled(RecommendationRequest request) {
        return request != null && request.resumeText != null && !request.resumeText.isBlank()
                && !AppConfig.get("QWEN_API_KEY", "").isBlank()
                && !AppConfig.get("QWEN_BASE_URL", "").isBlank()
                && !AppConfig.get("QWEN_MODEL", "").isBlank();
    }

    private String callModel(List<JobRecommendation> jobs, RecommendationRequest request)
            throws Exception {
        String endpoint = AppConfig.get("QWEN_BASE_URL", "").replaceAll("/+$", "")
                + "/chat/completions";
        JsonObject body = new JsonObject();
        body.addProperty("model", AppConfig.getRequired("QWEN_MODEL"));
        body.addProperty("temperature", 0.1);
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt());
        messages.add(system);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", buildPrompt(jobs, request));
        messages.add(user);
        body.add("messages", messages);

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + AppConfig.getRequired("QWEN_API_KEY"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM provider returned status " + response.statusCode());
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        return root.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    private void applyModelResults(List<JobRecommendation> jobs, String rawContent) {
        String json = rawContent == null ? "" : rawContent.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray results = root.getAsJsonArray("matches");
        Map<String, JobRecommendation> byKey = new HashMap<>();
        for (JobRecommendation job : jobs) {
            byKey.put(job.jobKey, job);
        }
        for (JsonElement element : results) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject result = element.getAsJsonObject();
            JobRecommendation job = byKey.get(string(result, "jobKey"));
            if (job == null) {
                continue;
            }
            int llmScore = Math.max(0, Math.min(100, integer(result, "matchScore", job.matchScore)));
            job.llmMatchScore = llmScore;
            job.llmRationale = truncate(string(result, "rationale"), 500);
            job.resumeAdvice = truncate(string(result, "resumeAdvice"), 600);
            job.llmEvaluated = true;
            job.matchScore = (int) Math.round(job.matchScore * 0.35 + llmScore * 0.65);
            if (job.llmRationale != null && !job.llmRationale.isBlank()) {
                job.matchReasons = new ArrayList<>(job.matchReasons);
                job.matchReasons.add("LLM review: " + job.llmRationale);
            }
        }
    }

    private String systemPrompt() {
        return "You are a careful US job-search reviewer. Judge transferable skills and equivalent "
                + "experience, not only exact keywords. Never invent experience. Return JSON only with "
                + "a matches array. Each item must contain jobKey, matchScore (0-100), rationale "
                + "(one concise sentence), and resumeAdvice (two concise sentences max). Advice must "
                + "identify the highest-impact truthful resume change for this specific job; if no "
                + "change is needed, say so briefly.";
    }

    private String buildPrompt(List<JobRecommendation> jobs, RecommendationRequest request) {
        StringBuilder prompt = new StringBuilder("TARGET ROLE: ").append(request.role)
                .append("\\nEXPERIENCE LEVEL: ").append(request.experienceLevel)
                .append("\\nRESUME:\\n").append(truncate(request.resumeText, MAX_RESUME_CHARS))
                .append("\\n\\nJOBS:\\n");
        for (JobRecommendation job : jobs) {
            prompt.append("JOB KEY: ").append(job.jobKey).append("\\nTITLE: ").append(job.title)
                    .append("\\nCOMPANY: ").append(job.company).append("\\nLOCATION: ").append(job.location)
                    .append("\\nDESCRIPTION: ").append(truncate(job.description, MAX_DESCRIPTION_CHARS))
                    .append("\\n---\\n");
        }
        return prompt.toString();
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

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "…";
    }
}
