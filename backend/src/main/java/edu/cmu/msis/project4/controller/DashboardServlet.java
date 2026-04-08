package edu.cmu.msis.project4.controller;

import edu.cmu.msis.project4.service.RecommendationService;
import org.bson.Document;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Author: Raina Qiu (yuluq)
 */
@WebServlet(name = "DashboardServlet", urlPatterns = "/dashboard")
public class DashboardServlet extends HttpServlet {
    private final RecommendationService recommendationService = new RecommendationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        List<Document> logs = recommendationService.dashboardLogs(100);
        req.setAttribute("logs", logs);
        req.setAttribute("totalRequests", logs.size());
        req.setAttribute("successCount", countByStatus(logs, 200));
        req.setAttribute("failureCount", logs.size() - countByStatus(logs, 200));
        req.setAttribute("averageApiLatencyMs", averageOf(logs, "thirdPartyLatencyMs"));
        req.setAttribute("averageReturnedCount", averageOf(logs, "returnedCount"));
        req.setAttribute("topRole", topRole(logs));
        req.setAttribute("applyCoveragePercent", applyCoveragePercent(logs));
        req.setAttribute("nationwideRequestCount", countByStrategy(logs, "us_nationwide"));
        req.setAttribute("topResolvedLocation", topResolvedLocation(logs));
        req.getRequestDispatcher("/WEB-INF/jsp/dashboard.jsp").forward(req, resp);
    }

    private long countByStatus(List<Document> logs, int statusCode) {
        return logs.stream()
                .filter(log -> ((Number) log.getOrDefault("statusCode", 500)).intValue() == statusCode)
                .count();
    }

    private long averageOf(List<Document> logs, String fieldName) {
        return Math.round(logs.stream()
                .mapToLong(log -> ((Number) log.getOrDefault(fieldName, 0)).longValue())
                .average()
                .orElse(0));
    }

    private String topRole(List<Document> logs) {
        return logs.stream()
                .map(log -> log.getString("inputRole"))
                .filter(role -> role != null && !role.isBlank())
                .collect(java.util.stream.Collectors.groupingBy(role -> role, java.util.stream.Collectors.counting()))
                .entrySet()
                .stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse("N/A");
    }

    private long applyCoveragePercent(List<Document> logs) {
        long returnedJobs = logs.stream()
                .mapToLong(log -> ((Number) log.getOrDefault("returnedCount", 0)).longValue())
                .sum();
        if (returnedJobs == 0) {
            return 0;
        }

        long jobsWithApplyLinks = logs.stream()
                .mapToLong(log -> ((Number) log.getOrDefault("jobsWithApplyLinks", 0)).longValue())
                .sum();
        return Math.round((jobsWithApplyLinks * 100.0) / returnedJobs);
    }

    private long countByStrategy(List<Document> logs, String strategy) {
        return logs.stream()
                .filter(log -> strategy.equalsIgnoreCase(log.getString("searchStrategy")))
                .count();
    }

    private String topResolvedLocation(List<Document> logs) {
        return logs.stream()
                .map(log -> log.getString("resolvedLocation"))
                .filter(location -> location != null && !location.isBlank())
                .collect(java.util.stream.Collectors.groupingBy(location -> location, java.util.stream.Collectors.counting()))
                .entrySet()
                .stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse("N/A");
    }
}
