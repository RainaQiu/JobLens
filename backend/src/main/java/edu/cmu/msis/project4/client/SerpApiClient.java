package edu.cmu.msis.project4.client;

import com.google.gson.JsonArray;
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
        String query = buildQuery(role, location, experienceLevel);

        String url = baseUrl
                + "?engine=" + encode(AppConfig.get("SERPAPI_ENGINE", "google_jobs"))
                + "&q=" + encode(query)
                + "&api_key=" + encode(apiKey);

        Instant start = Instant.now();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
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

        for (int i = 0; i < results.size(); i++) {
            JsonObject item = results.get(i).getAsJsonObject();
            JobRecommendation job = new JobRecommendation();
            job.title = getString(item, "title");
            job.company = getString(item, "company_name");
            job.location = getString(item, "location");
            job.applyLink = getApplyLink(item);
            job.postedAt = getPostedAt(item);
            job.reason = "Matches your selected role and location.";
            job.jobKey = normalize(job.title) + "|" + normalize(job.company) + "|" + normalize(job.location);

            if (isLikelyFresh(item)) {
                jobs.add(job);
            }
        }

        return jobs;
    }

    private boolean isLikelyFresh(JsonObject item) {
        if (!item.has("detected_extensions")) {
            return false;
        }
        JsonObject ext = item.getAsJsonObject("detected_extensions");
        String posted = ext.has("posted_at") ? ext.get("posted_at").getAsString().toLowerCase(Locale.US) : "";
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
        if (!item.has("related_links")) {
            return "";
        }
        JsonArray links = item.getAsJsonArray("related_links");
        if (links.size() == 0) {
            return "";
        }
        JsonObject first = links.get(0).getAsJsonObject();
        return getString(first, "link");
    }

    private String getPostedAt(JsonObject item) {
        if (!item.has("detected_extensions")) {
            return "";
        }
        JsonObject ext = item.getAsJsonObject("detected_extensions");
        return ext.has("posted_at") ? ext.get("posted_at").getAsString() : "";
    }

    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
    }

    private String buildQuery(String role, String location, String experienceLevel) {
        StringBuilder query = new StringBuilder();
        query.append(role == null ? "" : role.trim());
        if (experienceLevel != null && !experienceLevel.isBlank() && !"Any".equalsIgnoreCase(experienceLevel.trim())) {
            query.append(' ').append(experienceLevel.trim());
        }
        if (location != null && !location.isBlank()) {
            query.append(' ').append(location.trim());
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
