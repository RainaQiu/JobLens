package edu.cmu.msis.project4.controller;

import com.google.gson.Gson;
import edu.cmu.msis.project4.model.RecommendationRequest;
import edu.cmu.msis.project4.model.RecommendationResponse;
import edu.cmu.msis.project4.model.ClientRequestContext;
import edu.cmu.msis.project4.service.RecommendationService;
import edu.cmu.msis.project4.service.ThirdPartyApiException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Author: Raina Qiu (yuluq)
 */
@WebServlet(name = "RecommendationServlet", urlPatterns = "/api/recommendations")
public class RecommendationServlet extends HttpServlet {
    private final RecommendationService recommendationService = new RecommendationService();
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("application/json");

        try {
            RecommendationRequest request = gson.fromJson(req.getReader(), RecommendationRequest.class);
            RecommendationResponse response = recommendationService.recommend(request, buildClientContext(req));
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(response));
        } catch (IllegalArgumentException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (ThirdPartyApiException e) {
            writeError(resp, HttpServletResponse.SC_BAD_GATEWAY, e.getMessage());
        } catch (IllegalStateException e) {
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Server configuration is incomplete. Check environment variables.");
        } catch (Exception e) {
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The server could not process your request.");
        }
    }

    private ClientRequestContext buildClientContext(HttpServletRequest req) {
        ClientRequestContext context = new ClientRequestContext();
        context.endpoint = req.getRequestURI();
        context.deviceModel = valueOrEmpty(req.getHeader("X-Device-Model"));
        context.appVersion = valueOrEmpty(req.getHeader("X-App-Version"));
        context.clientIp = valueOrEmpty(req.getRemoteAddr());
        context.userAgent = valueOrEmpty(req.getHeader("User-Agent"));
        return context;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void writeError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().write(gson.toJson(Collections.singletonMap("error", message)));
    }
}
