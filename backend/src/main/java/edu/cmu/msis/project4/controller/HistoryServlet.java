package edu.cmu.msis.project4.controller;

import com.google.gson.Gson;
import edu.cmu.msis.project4.model.HistoryItem;
import edu.cmu.msis.project4.service.RecommendationService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Collections;

/**
 * Author: Raina Qiu (yuluq)
 */
@WebServlet(name = "HistoryServlet", urlPatterns = "/api/history")
public class HistoryServlet extends HttpServlet {
    private final RecommendationService recommendationService = new RecommendationService();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("application/json");

        try {
            String userId = req.getParameter("userId");
            List<HistoryItem> history = recommendationService.history(userId);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(history));
        } catch (IllegalArgumentException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The server could not load recommendation history.");
        }
    }

    private void writeError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().write(gson.toJson(Collections.singletonMap("error", message)));
    }
}
