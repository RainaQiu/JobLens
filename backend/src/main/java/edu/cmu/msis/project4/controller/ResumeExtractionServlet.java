package edu.cmu.msis.project4.controller;

import com.google.gson.Gson;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet(name = "ResumeExtractionServlet", urlPatterns = "/api/resumes/extract")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class ResumeExtractionServlet extends HttpServlet {
    private static final int MAX_TEXT_LENGTH = 30_000;
    private final Tika tika = new Tika();
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            Part file = req.getPart("resume");
            if (file == null || file.getSize() == 0) {
                throw new IllegalArgumentException("A resume file is required.");
            }
            String text;
            try (var input = file.getInputStream()) {
                text = tika.parseToString(input, new Metadata(), MAX_TEXT_LENGTH);
            }
            text = text.replaceAll("\\s+", " ").trim();
            if (text.isBlank()) {
                throw new IllegalArgumentException("No readable text was found in the resume.");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("filename", file.getSubmittedFileName());
            result.put("characterCount", text.length());
            result.put("text", text);
            resp.getWriter().write(gson.toJson(result));
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(Map.of("error", e.getMessage())));
        } catch (Exception e) {
            resp.setStatus(422);
            resp.getWriter().write(gson.toJson(Map.of("error", "The resume could not be parsed.")));
        }
    }
}
