package edu.cmu.msis.project4.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cmu.msis.project4.config.AppConfig;
import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.service.ThirdPartyApiException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Raina Qiu (yuluq)
 */
public class SerpApiClient {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FetchResult searchJobs(String role, String location, String experienceLevel)
            throws IOException, InterruptedException, ThirdPartyApiException {
        String baseUrl = AppConfig.get("SERPAPI_BASE_URL", "https://serpapi.com/search.json");
        String apiKey = AppConfig.getRequired("SERPAPI_API_KEY");
        String query = buildQuery(role, experienceLevel);

        StringBuilder url = new StringBuilder(baseUrl)
                .append("?engine=").append(encode(AppConfig.get("SERPAPI_ENGINE", "google_jobs")))
                .append("&q=").append(encode(query))
                .append("&api_key=").append(encode(apiKey));

        if (location != null && !location.isBlank()) {
            url.append("&location=").append(encode(location));
        }

        Instant start = Instant.now();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = Duration.between(start, Instant.now()).toMillis();

        if (response.statusCode() != 200) {
            throw new ThirdPartyApiException(
                    "SerpAPI returned status " + response.statusCode() + ".",
                    response.statusCode(),
                    latency,
                    "third_party_http_error");
        }

        try {
            List<JobRecommendation> jobs = parseJobs(response.body());
            return new FetchResult(jobs, latency, response.statusCode());
        } catch (RuntimeException e) {
            throw new ThirdPartyApiException(
                    "SerpAPI returned invalid data.",
                    response.statusCode(),
                    latency,
                    "third_party_invalid_data");
        }
    }

    private List<JobRecommendation> parseJobs(String rawJson) {
        List<JobRecommendation> jobs = new ArrayList<>();
        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
        JsonArray results = root.has("jobs_results") ? root.getAsJsonArray("jobs_results") : new JsonArray();

        for (JsonElement element : results) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject item = element.getAsJsonObject();
            if (!isLikelyFresh(item)) {
                continue;
            }

            JobRecommendation job = new JobRecommendation();
            job.title = getString(item, "title");
            job.company = getString(item, "company_name");
            job.location = getString(item, "location");
            job.postedAt = getPostedAt(item);
            job.applyLink = getApplyLink(item);
            job.applySource = getApplySource(item);
            job.shareLink = getString(item, "share_link");
            job.workMode = getWorkMode(item);
            job.employmentType = getEmploymentType(item);
            job.jobKey = buildJobKey(item, job);

            jobs.add(job);
        }

        return jobs;
    }

    private String buildJobKey(JsonObject item, JobRecommendation job) {
        String jobId = getString(item, "job_id");
        if (!jobId.isBlank()) {
            return jobId;
        }
        return normalize(job.title) + "|" + normalize(job.company) + "|" + normalize(job.location);
    }

    private boolean isLikelyFresh(JsonObject item) {
        String posted = getPostedAt(item).toLowerCase(Locale.US);
        if (posted.isBlank()) {
            return false;
        }
        if (posted.contains("today") || posted.contains("just") || posted.contains("yesterday")) {
            return true;
        }

        Matcher matcher = Pattern.compile("(\\d+)\\+?\\s+(hour|hours|day|days|week|weeks|month|months)")
                .matcher(posted);
        if (!matcher.find()) {
            return false;
        }

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);
        if (unit.startsWith("hour")) {
            return true;
        }
        if (unit.startsWith("day")) {
            return amount <= 7;
        }
        if (unit.startsWith("week")) {
            return amount <= 1;
        }
        return false;
    }

    private String getApplyLink(JsonObject item) {
        if (item.has("apply_options")) {
            JsonArray applyOptions = item.getAsJsonArray("apply_options");
            for (JsonElement element : applyOptions) {
                if (!element.isJsonObject()) {
                    continue;
                }
                String link = getString(element.getAsJsonObject(), "link");
                if (!link.isBlank()) {
                    return link;
                }
            }
        }

        String shareLink = getString(item, "share_link");
        if (!shareLink.isBlank()) {
            return shareLink;
        }

        if (item.has("related_links")) {
            JsonArray links = item.getAsJsonArray("related_links");
            for (JsonElement element : links) {
                if (!element.isJsonObject()) {
                    continue;
                }
                String link = getString(element.getAsJsonObject(), "link");
                if (!link.isBlank()) {
                    return link;
                }
            }
        }
        return "";
    }

    private String getApplySource(JsonObject item) {
        if (item.has("apply_options")) {
            JsonArray applyOptions = item.getAsJsonArray("apply_options");
            for (JsonElement element : applyOptions) {
                if (!element.isJsonObject()) {
                    continue;
                }
                String title = getString(element.getAsJsonObject(), "title");
                if (!title.isBlank()) {
                    return title;
                }
            }
        }

        String via = getString(item, "via");
        return via.isBlank() ? "Google Jobs" : via;
    }

    private String getWorkMode(JsonObject item) {
        JsonObject detectedExtensions = getObject(item, "detected_extensions");
        if (detectedExtensions != null
                && detectedExtensions.has("work_from_home")
                && !detectedExtensions.get("work_from_home").isJsonNull()
                && detectedExtensions.get("work_from_home").getAsBoolean()) {
            return "Remote";
        }

        for (String token : extensionTokens(item)) {
            String normalized = token.toLowerCase(Locale.US);
            if (normalized.contains("remote")) {
                return "Remote";
            }
            if (normalized.contains("hybrid")) {
                return "Hybrid";
            }
            if (normalized.contains("on-site") || normalized.contains("onsite")) {
                return "On-site";
            }
        }
        return "";
    }

    private String getEmploymentType(JsonObject item) {
        for (String token : extensionTokens(item)) {
            String normalized = token.toLowerCase(Locale.US);
            if (normalized.contains("intern")) {
                return "Internship";
            }
            if (normalized.contains("full-time")) {
                return "Full-time";
            }
            if (normalized.contains("part-time")) {
                return "Part-time";
            }
            if (normalized.contains("contract")) {
                return "Contract";
            }
            if (normalized.contains("temporary")) {
                return "Temporary";
            }
        }
        return "";
    }

    private List<String> extensionTokens(JsonObject item) {
        List<String> tokens = new ArrayList<>();
        if (item.has("extensions")) {
            JsonArray extensions = item.getAsJsonArray("extensions");
            for (JsonElement extension : extensions) {
                if (!extension.isJsonNull()) {
                    tokens.add(extension.getAsString());
                }
            }
        }

        JsonObject detectedExtensions = getObject(item, "detected_extensions");
        if (detectedExtensions != null) {
            String scheduleType = getString(detectedExtensions, "schedule_type");
            if (!scheduleType.isBlank()) {
                tokens.add(scheduleType);
            }
        }
        return tokens;
    }

    private String getPostedAt(JsonObject item) {
        JsonObject detectedExtensions = getObject(item, "detected_extensions");
        if (detectedExtensions == null) {
            return "";
        }
        return getString(detectedExtensions, "posted_at");
    }

    private JsonObject getObject(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonObject() ? obj.getAsJsonObject(key) : null;
    }

    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
    }

    private String buildQuery(String role, String experienceLevel) {
        StringBuilder query = new StringBuilder();
        query.append(role == null ? "" : role.trim());
        if (experienceLevel != null && !experienceLevel.isBlank() && !"Any".equalsIgnoreCase(experienceLevel.trim())) {
            query.append(' ').append(experienceLevel.trim());
        }
        return query.toString().trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static class FetchResult {
        public final List<JobRecommendation> jobs;
        public final long latencyMs;
        public final int statusCode;

        public FetchResult(List<JobRecommendation> jobs, long latencyMs, int statusCode) {
            this.jobs = jobs;
            this.latencyMs = latencyMs;
            this.statusCode = statusCode;
        }
    }
}
