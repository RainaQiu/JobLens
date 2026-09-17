package ds.edu.cmu.model;

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
    public List<String> matchReasons;
    public String eligibilityStatus;
    public List<String> eligibilityReasons;
    public String detectedCareerTrack;
    public String detectedSpecialization;
    public int roleFitScore;
    public int requiredSkillScore;
    public int preferredSkillScore;
    public int evidenceFitScore;
    public int deterministicScore;
    public String scoringVersion;
    public List<String> requiredSkillMatches;
    public List<String> missingRequiredSkills;
    public List<String> preferredSkillMatches;
    public List<String> evidenceHighlights;
    public List<String> transferableSkills;
    public List<String> missingSkills;
    public String currentBullet;
    public String suggestedBullet;
    public String resumeAdvice;
    public Integer llmMatchScore;
    public Double llmConfidence;
    public boolean llmEvaluated;
}
