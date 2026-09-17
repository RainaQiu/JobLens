package edu.cmu.msis.project4.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: Raina Qiu (yuluq)
 */
public class JobRecommendation {
    public String jobKey;
    public String title;
    public String company;
    public String location;
    public String postedAt;
    public String applyLink;
    public String applySource;
    public String shareLink;
    public String workMode;
    public String employmentType;
    public String description;
    public int matchScore;
    public List<String> matchReasons = new ArrayList<>();
    public Integer llmMatchScore;
    public String llmRationale;
    public String resumeAdvice;
    public boolean llmEvaluated;
    public String eligibilityStatus = "UNKNOWN";
    public List<String> eligibilityReasons = new ArrayList<>();
    public String detectedCareerTrack;
    public String detectedSpecialization;
    public int roleFitScore;
    public int requiredSkillScore;
    public int preferredSkillScore;
    public int evidenceFitScore;
    public int deterministicScore;
    public String scoringVersion = "hybrid-v1";
    public List<String> requiredSkillMatches = new ArrayList<>();
    public List<String> missingRequiredSkills = new ArrayList<>();
    public List<String> preferredSkillMatches = new ArrayList<>();
    public List<String> evidenceHighlights = new ArrayList<>();
    public List<String> transferableSkills = new ArrayList<>();
    public List<String> missingSkills = new ArrayList<>();
    public String currentBullet;
    public String suggestedBullet;
    public Double llmConfidence;
}
