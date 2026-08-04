package edu.cmu.msis.project4.controller;

import com.google.gson.Gson;
import edu.cmu.msis.project4.model.UserPreference;
import edu.cmu.msis.project4.repository.MongoRepository;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.regex.Pattern;

@WebServlet(name = "SubscriptionServlet", urlPatterns = "/api/subscriptions")
public class SubscriptionServlet extends HttpServlet {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final MongoRepository repository = new MongoRepository();
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            UserPreference preference = gson.fromJson(req.getReader(), UserPreference.class);
            validate(preference);
            repository.savePreference(preference);
            resp.getWriter().write(gson.toJson(Collections.singletonMap("status", "subscribed")));
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(Collections.singletonMap("error", e.getMessage())));
        }
    }

    private void validate(UserPreference preference) {
        if (preference == null || blank(preference.userId) || blank(preference.email)
                || blank(preference.role) || blank(preference.location)) {
            throw new IllegalArgumentException("userId, email, role, and location are required.");
        }
        if (!EMAIL.matcher(preference.email).matches()) {
            throw new IllegalArgumentException("A valid email address is required.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
