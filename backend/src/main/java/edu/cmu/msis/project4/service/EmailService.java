package edu.cmu.msis.project4.service;

import com.google.gson.Gson;
import edu.cmu.msis.project4.config.AppConfig;
import edu.cmu.msis.project4.model.JobRecommendation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sends a compact daily digest through Resend's HTTPS API (SMTP is not required). */
public class EmailService {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Gson gson = new Gson();

    public void sendDigest(String userId, String recipient, List<JobRecommendation> jobs) throws Exception {
        String apiKey = AppConfig.getRequired("RESEND_API_KEY");
        String from = AppConfig.getRequired("DIGEST_FROM_EMAIL");
        String apiUrl = AppConfig.get("RESEND_API_URL", "https://api.resend.com/emails");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", List.of(recipient));
        payload.put("subject", "Your " + jobs.size() + " JobLens matches for today");
        payload.put("html", renderHtml(jobs));

        String idempotencyKey = "joblens-" + sanitizeKey(userId) + "-"
                + LocalDate.now(ZoneOffset.UTC);
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Email provider returned status " + response.statusCode());
        }
    }

    String renderHtml(List<JobRecommendation> jobs) {
        StringBuilder html = new StringBuilder("<h1>Your JobLens matches</h1>")
                .append("<p>Fresh roles ranked against your profile. Review the score, then apply directly.</p>");
        for (JobRecommendation job : jobs) {
            html.append("<section style=\"margin:20px 0;padding:16px;border:1px solid #d8e2dc;border-radius:12px\">")
                    .append("<h2 style=\"margin:0\">").append(escape(job.title)).append("</h2>")
                    .append("<p>").append(escape(job.company)).append(" · ").append(escape(job.location)).append("</p>")
                    .append("<p><strong>").append(job.matchScore).append("% match</strong></p>");
            if (job.matchReasons != null && !job.matchReasons.isEmpty()) {
                html.append("<p>").append(escape(String.join(" · ", job.matchReasons))).append("</p>");
            }
            if (job.resumeAdvice != null && !job.resumeAdvice.isBlank()) {
                html.append("<p><strong>Resume tip:</strong> ").append(escape(job.resumeAdvice)).append("</p>");
            }
            html.append("<a href=\"").append(escapeAttribute(job.applyLink)).append("\">Apply now</a></section>");
        }
        return html.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escapeAttribute(String value) {
        return escape(value);
    }

    private String sanitizeKey(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
