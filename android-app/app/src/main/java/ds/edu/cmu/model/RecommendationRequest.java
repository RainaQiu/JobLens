package ds.edu.cmu.model;

/**
 * Author: Raina Qiu (yuluq)
 */
public class RecommendationRequest {
    public String userId;
    public String role;
    public String location;
    public String careerTrack;
    public String specialization;
    /** Legacy request field retained for older backend versions. */
    public String experienceLevel;
    public String searchScope;
}
