package edu.cmu.msis.project4.config;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Author: Raina Qiu (yuluq)
 * Validates required environment variables on app startup.
 */
@WebListener
public class ConfigValidationListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        AppConfig.getRequired("SERPAPI_API_KEY");
        AppConfig.getRequired("MONGODB_URI");
    }
}

