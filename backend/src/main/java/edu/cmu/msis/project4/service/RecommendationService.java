package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.client.SerpApiClient;
import edu.cmu.msis.project4.config.AppConfig;
import edu.cmu.msis.project4.model.ClientRequestContext;
import edu.cmu.msis.project4.model.HistoryItem;
import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.model.RecommendationRequest;
import edu.cmu.msis.project4.model.RecommendationResponse;
import edu.cmu.msis.project4.repository.MongoRepository;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Author: Raina Qiu (yuluq)
 */
public class RecommendationService {
    private final MongoRepository repository = new MongoRepository();
    private final SerpApiClient serpApiClient = new SerpApiClient();

    public RecommendationResponse recommend(RecommendationRequest request, ClientRequestContext clientContext)
            throws Exception {
        validate(request);

        String requestId = "req_" + UUID.randomUUID();
        SerpApiClient.FetchResult fetchResult;
        try {
            fetchResult = serpApiClient.searchJobs(request.role, request.location, request.experienceLevel);
        } catch (ThirdPartyApiException e) {
            repository.saveLog(buildBaseLog(requestId, request, clientContext)
                    .append("thirdPartyStatus", e.getStatusCode())
                    .append("thirdPartyLatencyMs", e.getLatencyMs())
                    .append("thirdPartyResultCount", 0)
                    .append("returnedCount", 0)
                    .append("statusCode", 502)
                    .append("errorType", e.getErrorType()));
            throw e;
        }

        int maxReturned = Integer.parseInt(AppConfig.get("MAX_RESULTS_RETURNED", "10"));
        List<JobRecommendation> filtered = new ArrayList<>();
        for (JobRecommendation job : fetchResult.jobs) {
            if (filtered.size() >= maxReturned) {
                break;
            }
            if (repository.alreadyRecommended(request.userId, job.jobKey)) {
                continue;
            }
            filtered.add(job);
            repository.saveRecommendationHistory(request.userId, job);
        }

        RecommendationResponse response = new RecommendationResponse();
        response.requestId = requestId;
        response.jobs = filtered;
        response.recommendedCount = filtered.size();
        response.meta.apiLatencyMs = fetchResult.latencyMs;

        repository.saveLog(buildBaseLog(requestId, request, clientContext)
                .append("thirdPartyStatus", fetchResult.statusCode)
                .append("thirdPartyLatencyMs", fetchResult.latencyMs)
                .append("thirdPartyResultCount", fetchResult.jobs.size())
                .append("returnedCount", filtered.size())
                .append("statusCode", 200)
                .append("errorType", ""));

        return response;
    }

    public List<HistoryItem> history(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required.");
        }

        List<HistoryItem> historyItems = new ArrayList<>();
        for (Document document : repository.getHistory(userId)) {
            HistoryItem item = new HistoryItem();
            item.title = document.getString("title");
            item.company = document.getString("company");
            item.location = document.getString("location");
            item.postedAt = document.getString("postedAt");
            item.applyLink = document.getString("applyLink");
            item.recommendedAt = document.getString("recommendedAt");
            historyItems.add(item);
        }
        return historyItems;
    }

    public List<Document> dashboardLogs(int limit) {
        return repository.recentLogs(limit);
    }

    private void validate(RecommendationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (isBlank(request.userId) || isBlank(request.role) || isBlank(request.location)) {
            throw new IllegalArgumentException("userId, role, and location are required.");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Document buildBaseLog(String requestId, RecommendationRequest request, ClientRequestContext clientContext) {
        ClientRequestContext safeContext = clientContext == null ? new ClientRequestContext() : clientContext;

        return new Document("requestId", requestId)
                .append("endpoint", safeContext.endpoint == null ? "/api/recommendations" : safeContext.endpoint)
                .append("userId", request.userId)
                .append("inputRole", request.role)
                .append("inputLocation", request.location)
                .append("inputExperienceLevel", request.experienceLevel)
                .append("deviceModel", safeContext.deviceModel == null ? "" : safeContext.deviceModel)
                .append("appVersion", safeContext.appVersion == null ? "" : safeContext.appVersion)
                .append("clientIp", safeContext.clientIp == null ? "" : safeContext.clientIp)
                .append("userAgent", safeContext.userAgent == null ? "" : safeContext.userAgent)
                .append("createdAt", Instant.now().toString());
    }
}
