package edu.cmu.msis.project4.model;

import java.util.Locale;

/** User-facing recruiting track used for eligibility filtering. */
public enum CareerTrack {
    ANY,
    INTERNSHIP,
    NEW_GRADUATE,
    GENERAL_FULL_TIME;

    public static CareerTrack parse(String value, String legacyExperienceLevel) {
        String candidate = firstNonBlank(value, legacyExperienceLevel);
        String normalized = candidate.toLowerCase(Locale.US).trim();
        if (normalized.isBlank() || normalized.equals("any")) {
            return ANY;
        }
        if (normalized.contains("intern") || normalized.contains("co-op") || normalized.contains("coop")) {
            return INTERNSHIP;
        }
        if (normalized.contains("new grad") || normalized.contains("new graduate")
                || normalized.contains("early career") || normalized.contains("entry")) {
            return NEW_GRADUATE;
        }
        if (normalized.contains("full time") || normalized.contains("full-time")
                || normalized.contains("general")) {
            return GENERAL_FULL_TIME;
        }
        try {
            return valueOf(normalized.replace('-', '_').replace(' ', '_').toUpperCase(Locale.US));
        } catch (IllegalArgumentException ignored) {
            return ANY;
        }
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? (fallback == null ? "" : fallback) : value;
    }
}
