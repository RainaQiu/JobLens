package edu.cmu.msis.project4.controller;

import com.google.gson.Gson;
import edu.cmu.msis.project4.config.AppConfig;
import edu.cmu.msis.project4.service.DailyDigestService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

@WebServlet(name = "DigestRunServlet", urlPatterns = "/api/digests/run")
public class DigestRunServlet extends HttpServlet {
    private final DailyDigestService digestService = new DailyDigestService();
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String expected = "Bearer " + AppConfig.getRequired("DIGEST_TRIGGER_TOKEN");
        String supplied = req.getHeader("Authorization");
        if (supplied == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(gson.toJson(Collections.singletonMap("error", "Unauthorized")));
            return;
        }
        resp.getWriter().write(gson.toJson(digestService.run()));
    }
}
