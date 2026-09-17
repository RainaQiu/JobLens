package edu.cmu.msis.project4.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cmu.msis.project4.config.AppConfig;
import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.model.RecommendationRequest;
import edu.cmu.msis.project4.model.SearchProfile;

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

/** Optional OpenAI-compatible semantic review for transferable skills and resume advice. */
public class LlmReranker {
    private static final int MAX_CANDIDATES = 20;
    private static final int MAX_RESUME_CHARS = 8_000;
    private static final int MAX_DESCRIPTION_CHARS = 2_500;

    private final HttpClient httpClient;
    private final Gson gson;
    private final LlmResponseParser responseParser;

    public LlmReranker() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                new Gson(), new LlmResponseParser());
    }

    LlmReranker(HttpClient httpClient, Gson gson, LlmResponseParser responseParser) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.responseParser = responseParser;
    }

    public List<JobRecommendation> rerank(List<JobRecommendation> candidates, RecommendationRequest request) {
        SearchProfile profile = SearchProfile.from(request, new RoleProfileExpander());
        return rerank(candidates, request, profile);
    }

    public List<JobRecommendation> rerank(
            List<JobRecommendation> candidates,
            RecommendationRequest request,
            SearchProfile profile) {
        if (!isEnabled(request) || candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        int candidateCount = Math.min(MAX_CANDIDATES, candidates.size());
        List<JobRecommendation> topCandidates = new ArrayList<>(candidates.subList(0, candidateCount));
        try {
            String content = callModel(topCandidates, request, profile);
            applyModelResults(topCandidates, content);
            topCandidates.sort(Comparator.comparingInt((JobRecommendation job) -> job.matchScore).reversed()
                    .thenComparing(job -> safe(job.postedAt)));

            List<JobRecommendation> merged = new ArrayList<>(topCandidates);
            if (candidates.size() > candidateCount) {
                merged.addAll(candidates.subList(candidateCount, candidates.size()));
            }
            return merged;
        } catch (Exception ignored) {
            // Search remains available with the deterministic score when the optional provider is down.
            return candidates;
        }
    }

    private boolean isEnabled(RecommendationRequest request) {
        return request != null && request.resumeText != null && !request.resumeText.isBlank()
                && !AppConfig.get("QWEN_API_KEY", "").isBlank()
                && !AppConfig.get("QWEN_BASE_URL", "").isBlank()
                && !AppConfig.get("QWEN_MODEL", "").isBlank();
    }

    private String callModel(
            List<JobRecommendation> jobs,
            RecommendationRequest request,
            SearchProfile profile) throws Exception {
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
        user.addProperty("content", buildPrompt(jobs, request, profile));
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
        List<LlmResponseParser.Result> results = responseParser.parse(rawContent, jobs);
        Map<String, JobRecommendation> byKey = new HashMap<>();
        for (JobRecommendation job : jobs) {
            byKey.put(job.jobKey, job);
        }
        for (LlmResponseParser.Result result : results) {
            JobRecommendation job = byKey.get(result.jobKey);
            if (job == null) {
                continue;
            }
            job.llmMatchScore = result.semanticScore;
            job.llmRationale = result.rationale;
            job.transferableSkills = new ArrayList<>(result.transferableSkills);
            job.missingSkills = new ArrayList<>(result.missingSkills);
            job.currentBullet = result.currentBullet;
            job.suggestedBullet = result.suggestedBullet;
            job.resumeAdvice = result.resumeAdvice;
            job.llmConfidence = result.confidence;
            job.llmEvaluated = true;
            job.matchScore = (int) Math.round(job.deterministicScore * 0.55 + result.semanticScore * 0.45);
            if (result.rationale != null && !result.rationale.isBlank()) {
                job.matchReasons = new ArrayList<>(job.matchReasons);
                job.matchReasons.add("DeepSeek review: " + result.rationale);
            }
        }
    }

    private String systemPrompt() {
        return "You are a careful US job-search reviewer. The server has already verified the job's "
                + "career track, role family, specialization, and location; do not override those "
                + "eligibility decisions. Judge functional fit and transferable skills using only the "
                + "resume and job text. Never invent experience or claim a missing skill is present. "
                + "Return JSON only with a matches array. Each item must contain jobKey, semanticScore "
                + "(0-100), rationale (one concise sentence), transferableSkills, missingSkills, "
                + "currentBullet, suggestedBullet, resumeAdvice (two concise sentences maximum), and "
                + "confidence (0-1). currentBullet must be an exact or faithful excerpt from the "
                + "resume. suggestedBullet must be truthful and must not add unsupported metrics.";
    }

    private String buildPrompt(
            List<JobRecommendation> jobs,
            RecommendationRequest request,
            SearchProfile profile) {
        StringBuilder prompt = new StringBuilder("TARGET ROLE: ").append(safe(request == null ? null : request.role))
                .append("\nROLE FAMILY: ").append(safe(profile == null ? null : profile.roleFamily))
                .append("\nSPECIALIZATION: ").append(safe(profile == null ? null : profile.specialization))
                .append("\nCAREER TRACK: ").append(profile == null ? "ANY" : profile.careerTrack)
                .append("\nRESUME:\n").append(truncate(request == null ? null : request.resumeText, MAX_RESUME_CHARS))
                .append("\n\nJOBS:\n");
        for (JobRecommendation job : jobs) {
            prompt.append("JOB KEY: ").append(job.jobKey)
                    .append("\nTITLE: ").append(job.title)
                    .append("\nCOMPANY: ").append(job.company)
                    .append("\nLOCATION: ").append(job.location)
                    .append("\nDETERMINISTIC SCORE: ").append(job.deterministicScore)
                    .append("\nDESCRIPTION: ").append(truncate(job.description, MAX_DESCRIPTION_CHARS))
                    .append("\n---\n");
        }
        return prompt.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
