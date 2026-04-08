package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.client.SerpApiClient;
import edu.cmu.msis.project4.config.AppConfig;
import edu.cmu.msis.project4.model.ClientRequestContext;
import edu.cmu.msis.project4.model.HistoryItem;
import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.model.RecommendationRequest;
import edu.cmu.msis.project4.model.RecommendationResponse;
import edu.cmu.msis.project4.model.ResolvedLocation;
import edu.cmu.msis.project4.repository.MongoRepository;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

/**
 * Author: Raina Qiu (yuluq)
 */
public class RecommendationService {
    private final MongoRepository repository = new MongoRepository();
    private final SerpApiClient serpApiClient = new SerpApiClient();
    private final LocationResolutionService locationResolutionService = new LocationResolutionService();

    public RecommendationResponse recommend(RecommendationRequest request, ClientRequestContext clientContext)
            throws Exception {
        validate(request);

        String requestId = "req_" + UUID.randomUUID();
        ResolvedLocation resolvedLocation = locationResolutionService.resolve(request.location, request.searchScope);
        SearchAggregation aggregation;
        try {
            aggregation = searchAcrossLocations(request, resolvedLocation);
        } catch (ThirdPartyApiException e) {
            repository.saveLog(buildBaseLog(requestId, request, clientContext, resolvedLocation)
                    .append("thirdPartyStatus", e.getStatusCode())
                    .append("thirdPartyLatencyMs", e.getLatencyMs())
                    .append("thirdPartyCallCount", 1)
                    .append("thirdPartyResultCount", 0)
                    .append("returnedCount", 0)
                    .append("jobsWithApplyLinks", 0)
                    .append("searchedLocationsCount", resolvedLocation.searchLocations.size())
                    .append("searchedLocations", String.join(" | ", resolvedLocation.searchLocations))
                    .append("resolvedLocation", resolvedLocation.resolvedLabel)
                    .append("resolvedLocationType", resolvedLocation.locationType)
                    .append("searchStrategy", resolvedLocation.searchStrategy)
                    .append("statusCode", 502)
                    .append("errorType", e.getErrorType()));
            throw e;
        }

        int maxReturned = Integer.parseInt(AppConfig.get("MAX_RESULTS_RETURNED", "50"));
        List<JobRecommendation> filtered = new ArrayList<>();
        Set<String> seenInThisResponse = new HashSet<>();
        for (JobRecommendation job : aggregation.uniqueJobs.values()) {
            if (filtered.size() >= maxReturned) {
                break;
            }
            if (job == null || job.jobKey == null || job.jobKey.isBlank() || !seenInThisResponse.add(job.jobKey)) {
                continue;
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
        response.meta.apiLatencyMs = aggregation.totalLatencyMs;
        response.meta.requestedSearchScope = normalizeScope(request.searchScope);
        response.meta.searchStrategy = resolvedLocation.searchStrategy;
        response.meta.resolvedLocation = resolvedLocation.resolvedLabel;
        response.meta.searchSummary = buildSearchSummary(resolvedLocation, aggregation.searchedLocations.size());
        response.meta.searchedLocationsCount = aggregation.searchedLocations.size();
        response.meta.jobsWithApplyLinks = countJobsWithApplyLinks(filtered);

        repository.saveLog(buildBaseLog(requestId, request, clientContext, resolvedLocation)
                .append("thirdPartyStatus", aggregation.statusCode)
                .append("thirdPartyLatencyMs", aggregation.totalLatencyMs)
                .append("thirdPartyCallCount", aggregation.thirdPartyCallCount)
                .append("thirdPartyResultCount", aggregation.thirdPartyResultCount)
                .append("returnedCount", filtered.size())
                .append("jobsWithApplyLinks", response.meta.jobsWithApplyLinks)
                .append("searchedLocationsCount", aggregation.searchedLocations.size())
                .append("searchedLocations", String.join(" | ", aggregation.searchedLocations))
                .append("resolvedLocation", resolvedLocation.resolvedLabel)
                .append("resolvedLocationType", resolvedLocation.locationType)
                .append("searchStrategy", resolvedLocation.searchStrategy)
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
            item.applySource = document.getString("applySource");
            item.shareLink = document.getString("shareLink");
            item.workMode = document.getString("workMode");
            item.employmentType = document.getString("employmentType");
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
        if (isBlank(request.userId) || isBlank(request.role)) {
            throw new IllegalArgumentException("userId and role are required.");
        }
        if (!LocationResolutionService.SCOPE_NATIONWIDE_US.equalsIgnoreCase(normalizeScope(request.searchScope))
                && isBlank(request.location)) {
            throw new IllegalArgumentException("location is required unless Nationwide U.S. search is selected.");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Document buildBaseLog(
            String requestId,
            RecommendationRequest request,
            ClientRequestContext clientContext,
            ResolvedLocation resolvedLocation) {
        ClientRequestContext safeContext = clientContext == null ? new ClientRequestContext() : clientContext;
        ResolvedLocation safeLocation = resolvedLocation == null ? new ResolvedLocation() : resolvedLocation;

        return new Document("requestId", requestId)
                .append("endpoint", safeContext.endpoint == null ? "/api/recommendations" : safeContext.endpoint)
                .append("userId", request.userId)
                .append("inputRole", request.role)
                .append("inputLocation", request.location)
                .append("inputExperienceLevel", request.experienceLevel)
                .append("inputSearchScope", normalizeScope(request.searchScope))
                .append("resolvedLocation", safeLocation.resolvedLabel == null ? "" : safeLocation.resolvedLabel)
                .append("resolvedLocationType", safeLocation.locationType == null ? "" : safeLocation.locationType)
                .append("searchStrategy", safeLocation.searchStrategy == null ? "" : safeLocation.searchStrategy)
                .append("deviceModel", safeContext.deviceModel == null ? "" : safeContext.deviceModel)
                .append("appVersion", safeContext.appVersion == null ? "" : safeContext.appVersion)
                .append("clientIp", safeContext.clientIp == null ? "" : safeContext.clientIp)
                .append("userAgent", safeContext.userAgent == null ? "" : safeContext.userAgent)
                .append("createdAt", Instant.now().toString());
    }

    private SearchAggregation searchAcrossLocations(RecommendationRequest request, ResolvedLocation resolvedLocation)
            throws Exception {
        SearchAggregation aggregation = new SearchAggregation();
        int maxReturned = Integer.parseInt(AppConfig.get("MAX_RESULTS_RETURNED", "50"));
        int minNationwideQueries = Integer.parseInt(AppConfig.get("NATIONWIDE_US_MIN_STATES", "4"));
        ThirdPartyApiException lastFailure = null;

        for (String location : resolvedLocation.searchLocations) {
            try {
                SerpApiClient.FetchResult fetchResult =
                        serpApiClient.searchJobs(request.role, location, request.experienceLevel);
                aggregation.statusCode = fetchResult.statusCode;
                aggregation.totalLatencyMs += fetchResult.latencyMs;
                aggregation.thirdPartyCallCount++;
                aggregation.thirdPartyResultCount += fetchResult.jobs.size();
                aggregation.searchedLocations.add(location.replace(",", ", "));

                for (JobRecommendation job : fetchResult.jobs) {
                    aggregation.uniqueJobs.putIfAbsent(job.jobKey, job);
                }

                if (resolvedLocation.isMultiLocation()
                        && aggregation.searchedLocations.size() >= minNationwideQueries
                        && aggregation.uniqueJobs.size() >= maxReturned * 2) {
                    break;
                }
            } catch (ThirdPartyApiException e) {
                lastFailure = e;
                if (!resolvedLocation.isMultiLocation() || isFatalThirdPartyStatus(e.getStatusCode())) {
                    throw e;
                }
            }
        }

        if (aggregation.thirdPartyCallCount == 0 && lastFailure != null) {
            throw lastFailure;
        }
        return aggregation;
    }

    private boolean isFatalThirdPartyStatus(int statusCode) {
        return statusCode == 401 || statusCode == 403 || statusCode == 429;
    }

    private int countJobsWithApplyLinks(List<JobRecommendation> jobs) {
        int count = 0;
        for (JobRecommendation job : jobs) {
            if (job != null && job.applyLink != null && !job.applyLink.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private String buildSearchSummary(ResolvedLocation resolvedLocation, int searchedLocationsCount) {
        if (resolvedLocation == null) {
            return "";
        }
        if ("us_nationwide".equalsIgnoreCase(resolvedLocation.searchStrategy)) {
            return "Searching nationwide across the United States via "
                    + searchedLocationsCount + " state-level searches.";
        }
        if ("Country".equalsIgnoreCase(resolvedLocation.locationType)) {
            return "Searching across " + resolvedLocation.resolvedLabel + ".";
        }
        if (resolvedLocation.resolvedLabel == null || resolvedLocation.resolvedLabel.isBlank()) {
            return "";
        }
        return "Using location: " + resolvedLocation.resolvedLabel + ".";
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return LocationResolutionService.SCOPE_AUTO;
        }
        String normalized = scope.trim().toUpperCase(Locale.US);
        if (LocationResolutionService.SCOPE_SPECIFIC.equals(normalized)
                || LocationResolutionService.SCOPE_NATIONWIDE_US.equals(normalized)) {
            return normalized;
        }
        return LocationResolutionService.SCOPE_AUTO;
    }

    private static class SearchAggregation {
        private final Map<String, JobRecommendation> uniqueJobs = new LinkedHashMap<>();
        private final List<String> searchedLocations = new ArrayList<>();
        private long totalLatencyMs;
        private int thirdPartyResultCount;
        private int thirdPartyCallCount;
        private int statusCode = 200;
    }
}
