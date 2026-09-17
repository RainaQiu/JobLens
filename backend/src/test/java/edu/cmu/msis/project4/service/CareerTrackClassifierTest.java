package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.CareerTrack;
import edu.cmu.msis.project4.model.JobRecommendation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerTrackClassifierTest {
    private final CareerTrackClassifier classifier = new CareerTrackClassifier();

    @Test
    void detectsInternshipAndCoopSignals() {
        JobRecommendation job = job("Software Engineer Intern", "Summer internship building Android features.", "Internship");

        CareerTrackClassifier.Result result = classifier.classify(job);

        assertEquals(CareerTrack.INTERNSHIP, result.track);
        assertTrue(result.explicit);
        assertTrue(result.signals.stream().anyMatch(signal -> signal.toLowerCase().contains("intern")));
    }

    @Test
    void detectsNewGraduateSignalsEvenWhenEmploymentTypeIsFullTime() {
        JobRecommendation job = job("Software Engineer, New Grad", "University hire program for recent graduates.", "Full-time");

        CareerTrackClassifier.Result result = classifier.classify(job);

        assertEquals(CareerTrack.NEW_GRADUATE, result.track);
        assertTrue(result.explicit);
    }

    @Test
    void classifiesUnmarkedFullTimeAsGeneralFullTime() {
        JobRecommendation job = job("Senior Platform Engineer", "Build production infrastructure.", "Full-time");

        CareerTrackClassifier.Result result = classifier.classify(job);

        assertEquals(CareerTrack.GENERAL_FULL_TIME, result.track);
        assertTrue(result.explicit);
    }

    @Test
    void leavesMissingMetadataUnknown() {
        JobRecommendation job = job("Software Engineer", "Build production services.", "");

        CareerTrackClassifier.Result result = classifier.classify(job);

        assertEquals(CareerTrack.ANY, result.track);
        assertTrue(!result.explicit);
    }

    private JobRecommendation job(String title, String description, String employmentType) {
        JobRecommendation job = new JobRecommendation();
        job.title = title;
        job.description = description;
        job.employmentType = employmentType;
        job.location = "United States";
        return job;
    }
}
