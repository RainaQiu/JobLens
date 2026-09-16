package edu.cmu.msis.project4.model;

/**
 * Author: Raina Qiu (yuluq)
 */
public class RecommendationRequest {
    public String userId;
    public String role;
    public String location;
    public String careerTrack;
    public String specialization;
    public String experienceLevel;
    public String searchScope;
    public String resumeText;
    public Integer limit;
    public Boolean persistHistory;
}
