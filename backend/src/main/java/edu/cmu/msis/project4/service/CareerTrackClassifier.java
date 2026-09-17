package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.CareerTrack;
import edu.cmu.msis.project4.model.JobRecommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Detects recruiting-track signals from SerpAPI metadata and job text. */
public class CareerTrackClassifier {
    public Result classify(JobRecommendation job) {
        String title = normalize(job == null ? null : job.title);
        String description = normalize(job == null ? null : job.description);
        String employmentType = normalize(job == null ? null : job.employmentType);
        String text = title + " " + description;
        List<String> signals = new ArrayList<>();

        if (containsAny(text, "intern", "internship", "co op", "coop")) {
            signals.add("Internship/co-op signal");
            return new Result(CareerTrack.INTERNSHIP, signals, true);
        }
        if (containsAny(text, "new grad", "new graduate", "early career", "university hire",
                "campus hire", "recent graduate", "rotational program", "graduating in")) {
            signals.add("New-graduate/early-career signal");
            return new Result(CareerTrack.NEW_GRADUATE, signals, true);
        }
        if (containsAny(employmentType, "full time", "full-time")
                || containsAny(text, "full time", "full-time")) {
            signals.add("Full-time signal");
            return new Result(CareerTrack.GENERAL_FULL_TIME, signals, true);
        }
        return new Result(CareerTrack.ANY, signals, false);
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    public static final class Result {
        public final CareerTrack track;
        public final List<String> signals;
        public final boolean explicit;

        public Result(CareerTrack track, List<String> signals, boolean explicit) {
            this.track = track == null ? CareerTrack.ANY : track;
            this.signals = List.copyOf(signals == null ? List.of() : signals);
            this.explicit = explicit;
        }
    }
}
