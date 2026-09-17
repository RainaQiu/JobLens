package edu.cmu.msis.project4.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of applying hard search constraints to one job. */
public final class EligibilityDecision {
    public enum Status {
        PASS,
        FAIL,
        UNKNOWN
    }

    public final Status status;
    public final List<String> reasons;
    public final CareerTrack detectedCareerTrack;
    public final String roleFamily;
    public final String specialization;

    public EligibilityDecision(
            Status status,
            List<String> reasons,
            CareerTrack detectedCareerTrack,
            String roleFamily,
            String specialization) {
        this.status = status == null ? Status.UNKNOWN : status;
        this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons == null ? List.of() : reasons));
        this.detectedCareerTrack = detectedCareerTrack == null ? CareerTrack.ANY : detectedCareerTrack;
        this.roleFamily = roleFamily == null ? "" : roleFamily;
        this.specialization = specialization == null ? "" : specialization;
    }
}
