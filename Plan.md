# JobLens Career-Track Hybrid Matching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current mixed seniority/score logic with career-track eligibility filtering, explainable role-and-skill ranking, DeepSeek transferable-skill review, and concrete resume bullet advice for the cloud-hosted JobLens product.

**Architecture:** Keep Render as the single public origin for the Java/Tomcat backend, browser app, and API. Normalize each request into a search profile, expand role and skill aliases from versioned taxonomy resources, retrieve and deduplicate SerpAPI jobs, apply a tri-state eligibility filter, calculate an auditable deterministic score, and send only the top 20 eligible candidates to the optional OpenAI-compatible DeepSeek adapter. The final score is deterministic-first (`55%` rules, `45%` LLM) and always falls back to rules when the provider is unavailable.

**Tech Stack:** Java 17, Servlet 4.0, Gson, Maven/JUnit 5, MongoDB driver, SerpAPI Google Jobs, DeepSeek OpenAI-compatible Chat Completions, static HTML/CSS/JavaScript, Docker/Tomcat 9, Render Blueprint, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-joblens-career-track-matching-design.md`

## Global Constraints

- User-facing career tracks are exactly `Internship`, `New Graduate / Early Career`, and `General Full-time`; server normalization also accepts `ANY`.
- `experienceLevel` remains accepted as a backward-compatible request alias; new clients send `careerTrack`.
- Career track, target role family/specialization, and location are eligibility conditions; they are not soft-score dimensions.
- Skill gaps affect ranking and advice but never hard-fail an otherwise eligible job.
- All score components and final scores are integers in `0..100`.
- DeepSeek receives at most 20 eligible candidates per request and must return JSON only; a provider failure must return deterministic results.
- Never put a real API key in Git, `Plan.md`, `.env.example`, test fixtures, logs, or GitHub Actions output.
- Existing endpoint paths remain stable: `/api/recommendations`, `/api/resumes/extract`, `/api/subscriptions`, `/api/digests/run`, and `/api/health`.
- Every implementation task ends with focused tests and a commit; run the full Maven test suite before integration verification.

---

## File map

### New files

- `backend/src/main/java/edu/cmu/msis/project4/model/CareerTrack.java` — internal enum and normalization helpers for the three user-facing tracks plus `ANY`.
- `backend/src/main/java/edu/cmu/msis/project4/model/EligibilityDecision.java` — immutable `PASS/FAIL/UNKNOWN` decision and reason list.
- `backend/src/main/java/edu/cmu/msis/project4/model/SearchProfile.java` — normalized request profile, role family, specialization, track, and expanded query variants.
- `backend/src/main/java/edu/cmu/msis/project4/model/MatchFeatures.java` — deterministic component scores and evidence lists.
- `backend/src/main/java/edu/cmu/msis/project4/service/CareerTrackClassifier.java` — classifies job text and metadata into the requested track.
- `backend/src/main/java/edu/cmu/msis/project4/service/RoleProfileExpander.java` — loads role aliases and produces bounded SerpAPI query variants.
- `backend/src/main/java/edu/cmu/msis/project4/service/JobEligibilityFilter.java` — applies role, career-track, and location decisions before scoring.
- `backend/src/main/java/edu/cmu/msis/project4/service/LlmResponseParser.java` — pure JSON parser/validator for DeepSeek match responses.
- `backend/src/main/resources/matching/role-aliases.json` — versioned role families and specialization aliases.
- `backend/src/main/resources/matching/skill-aliases.json` — canonical skills and phrase aliases.
- `backend/src/test/java/edu/cmu/msis/project4/service/CareerTrackTest.java` — request/legacy-field normalization cases.
- `backend/src/test/java/edu/cmu/msis/project4/service/CareerTrackClassifierTest.java` — classifier cases.
- `backend/src/test/java/edu/cmu/msis/project4/service/JobEligibilityFilterTest.java` — hard-filter cases.
- `backend/src/test/java/edu/cmu/msis/project4/service/RoleProfileExpanderTest.java` — role and skill alias expansion cases.
- `backend/src/test/java/edu/cmu/msis/project4/service/LlmResponseParserTest.java` — valid, partial, malformed, and out-of-range LLM responses.
- `backend/src/test/java/edu/cmu/msis/project4/service/RecommendationPipelineTest.java` — dependency-injected ordering and history cases.
- `backend/src/test/resources/fixtures/deepseek-matches.json` — synthetic, non-personal parser fixture.

### Modified files

- `backend/src/main/java/edu/cmu/msis/project4/model/RecommendationRequest.java` — add `careerTrack` and optional `specialization` while retaining `experienceLevel`.
- `backend/src/main/java/edu/cmu/msis/project4/model/JobRecommendation.java` — add eligibility status, component scores, skill evidence, and bullet advice fields.
- `backend/src/main/java/edu/cmu/msis/project4/model/RecommendationResponse.java` — add candidate/eligibility/LLM counts and `scoringVersion` metadata.
- `backend/src/main/java/edu/cmu/msis/project4/service/JobMatchingService.java` — replace the current title/resume coverage formula with role/required/preferred/evidence components.
- `backend/src/main/java/edu/cmu/msis/project4/service/LlmReranker.java` — use the new prompt/schema, final-score blend, and parser fallback.
- `backend/src/main/java/edu/cmu/msis/project4/service/RecommendationService.java` — orchestrate profile expansion, multi-query retrieval, eligibility filtering, deterministic ranking, LLM reranking, deduplication, history, and metadata.
- `backend/src/main/java/edu/cmu/msis/project4/client/SerpApiClient.java` — accept an expanded role query and normalized career-track tokens without duplicating them.
- `backend/src/main/java/edu/cmu/msis/project4/model/UserPreference.java` — persist `careerTrack` and optional `specialization` for daily digests.
- `backend/src/main/java/edu/cmu/msis/project4/repository/MongoRepository.java` — read/write persisted career-track fields and new score/advice fields.
- `backend/src/main/java/edu/cmu/msis/project4/service/DailyDigestService.java` — map saved preferences to the new request fields and select the score threshold.
- `backend/src/main/webapp/index.html` — send `careerTrack`, expose the three options, and render eligibility, score explanations, transferable skills, and concrete resume advice.
- `backend/src/test/java/edu/cmu/msis/project4/service/JobMatchingServiceTest.java` — assert component weights, role-specialization behavior, required/preferred skill separation, and score bounds.
- `android-app/app/src/main/java/ds/edu/cmu/FirstFragment.java` — send the new career-track value and preserve saved selections.
- `android-app/app/src/main/java/ds/edu/cmu/JobRecommendationAdapter.java` — show final score, eligibility, and concise advice in native cards.
- `android-app/app/src/main/res/layout/fragment_first.xml` — rename the search control from experience level to career track.
- `android-app/app/src/main/res/layout/item_job.xml` — add score/evidence/advice presentation fields.
- `android-app/app/src/main/res/values/arrays.xml` — replace experience-level options with the three career-track options.
- `android-app/app/src/main/res/values/strings.xml` — update labels and validation copy.
- `.env.example` — document DeepSeek values with empty secrets.
- `render.yaml` — declare optional `QWEN_API_KEY`, `QWEN_BASE_URL`, and `QWEN_MODEL` inputs.
- `.github/workflows/daily-digest.yml` — add non-secret URL/token preflight and normalized URL invocation.
- `README.md` — document career-track semantics, scoring, DeepSeek configuration, cloud demo URL setup, and fallback behavior.

---

## Task 1: Add the career-track domain contract

**Files:**
- Create: `backend/src/main/java/edu/cmu/msis/project4/model/CareerTrack.java`
- Create: `backend/src/main/java/edu/cmu/msis/project4/model/SearchProfile.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/model/RecommendationRequest.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/model/UserPreference.java`
- Test: `backend/src/test/java/edu/cmu/msis/project4/service/CareerTrackTest.java`

**Interfaces:**
- `CareerTrack.parse(String value, String legacyExperienceLevel)` returns `ANY`, `INTERNSHIP`, `NEW_GRADUATE`, or `GENERAL_FULL_TIME`.
- `SearchProfile.from(RecommendationRequest request, RoleProfileExpander expander)` returns a normalized profile with `role`, `specialization`, `careerTrack`, `location`, `searchScope`, and `queryVariants`.
- `RecommendationRequest` adds `public String careerTrack; public String specialization;` and retains `experienceLevel`.

`CareerTrackTest` is a standalone normalization test; job-text classification is covered separately in Task 3.

- [ ] **Step 1: Write failing normalization tests.** Add cases asserting `Internship`, `Entry level`, `New Graduate`, `Full-time`, blank, and `Any` map to the expected enum; assert `careerTrack` wins when both fields are present.

- [ ] **Step 2: Run the focused test.**

Run: `mvn --file backend/pom.xml -Dtest=CareerTrackTest test`

Expected: FAIL because `CareerTrack` and the new request fields do not exist.

- [ ] **Step 3: Implement the enum and request fields.** Use uppercase constants and a parser that lowercases/trims input, maps legacy aliases, and defaults blank values to `ANY`. Do not throw for unknown user values; normalize them to `ANY` and let request validation report only missing required role/location.

- [ ] **Step 4: Implement `SearchProfile.from`.** Normalize `role` and `specialization` to trimmed text, derive `careerTrack`, preserve `searchScope`, and leave query expansion to `RoleProfileExpander`. Add `careerTrack` and `specialization` to `UserPreference` so new subscriptions can persist the normalized user choice.

- [ ] **Step 5: Run the focused test again.**

Run: `mvn --file backend/pom.xml -Dtest=CareerTrackTest test`

Expected: PASS with every alias covered.

- [ ] **Step 6: Commit the domain contract.**

```bash
git add backend/src/main/java/edu/cmu/msis/project4/model/CareerTrack.java backend/src/main/java/edu/cmu/msis/project4/model/SearchProfile.java backend/src/main/java/edu/cmu/msis/project4/model/RecommendationRequest.java backend/src/test/java/edu/cmu/msis/project4/service/CareerTrackTest.java
git commit -m "feat: model job search career tracks"
```

## Task 2: Add role and skill taxonomy expansion

**Files:**
- Create: `backend/src/main/resources/matching/role-aliases.json`
- Create: `backend/src/main/resources/matching/skill-aliases.json`
- Create: `backend/src/main/java/edu/cmu/msis/project4/service/RoleProfileExpander.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/model/SearchProfile.java`
- Test: `backend/src/test/java/edu/cmu/msis/project4/service/RoleProfileExpanderTest.java`

**Interfaces:**
- `RoleProfileExpander.expand(String role, String specialization, String resumeText)` returns `SearchProfile.RoleExpansion` with canonical family, specialization, `List<String> queryVariants`, `Set<String> requiredSkillHints`, and `Set<String> preferredSkillHints`.
- Resource JSON uses `{ "family": "...", "aliases": ["..."], "specializations": { "...": ["..."] } }` for roles and `{ "canonical": "...", "aliases": ["..."] }` for skills.

`SearchProfile.RoleExpansion` is a public static value class with `canonicalFamily`, `canonicalSpecialization`, `queryVariants`, `requiredSkillHints`, and `preferredSkillHints` fields.

- [ ] **Step 1: Write failing expansion tests.** Assert `Software Engineer` produces bounded variants containing Backend, Platform, Android, iOS, and Full Stack; assert `Android Software Engineer` keeps Android-specific variants and does not add iOS; assert `Google Cloud BigQuery` resolves to canonical `bigquery`.

- [ ] **Step 2: Run the focused test.**

Run: `mvn --file backend/pom.xml -Dtest=RoleProfileExpanderTest test`

Expected: FAIL because taxonomy resources and expander do not exist.

- [ ] **Step 3: Add the resource files.** Include initial families for Data Scientist, Data Engineer, Software Engineer, and AI Agent Engineer; include the software specializations Backend, Platform, Android, iOS, and Full Stack; include aliases for Python, SQL, Spark, Airflow, AWS, GCP, BigQuery, PyTorch, TensorFlow, Docker, Kubernetes, RAG, LangChain, LLM, and ETL.

- [ ] **Step 4: Implement bounded resource loading.** Load resources from the classpath, normalize aliases to lowercase, preserve canonical labels for display, cap query variants at three per request, and never send the full resume to a query-expansion call.

- [ ] **Step 5: Run the focused test again.**

Run: `mvn --file backend/pom.xml -Dtest=RoleProfileExpanderTest test`

Expected: PASS and no test produces more than three query variants.

- [ ] **Step 6: Commit taxonomy expansion.**

```bash
git add backend/src/main/resources/matching backend/src/main/java/edu/cmu/msis/project4/service/RoleProfileExpander.java backend/src/main/java/edu/cmu/msis/project4/model/SearchProfile.java backend/src/test/java/edu/cmu/msis/project4/service/RoleProfileExpanderTest.java
git commit -m "feat: add role and skill alias expansion"
```

## Task 3: Implement career-track, role, and location eligibility filtering

**Files:**
- Create: `backend/src/main/java/edu/cmu/msis/project4/model/EligibilityDecision.java`
- Create: `backend/src/main/java/edu/cmu/msis/project4/service/CareerTrackClassifier.java`
- Create: `backend/src/main/java/edu/cmu/msis/project4/service/JobEligibilityFilter.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/model/JobRecommendation.java`
- Test: `backend/src/test/java/edu/cmu/msis/project4/service/CareerTrackClassifierTest.java`
- Test: `backend/src/test/java/edu/cmu/msis/project4/service/JobEligibilityFilterTest.java`

**Interfaces:**
- `CareerTrackClassifier.classify(JobRecommendation job)` returns a `CareerTrackClassifier.Result` containing a normalized `CareerTrack` and evidence signals from title, description, employment type, and work mode.
- `JobEligibilityFilter.evaluate(JobRecommendation job, SearchProfile profile, ResolvedLocation resolvedLocation)` returns `EligibilityDecision` with `status`, `reasons`, `detectedCareerTrack`, `roleFamily`, and `specialization`.

`CareerTrackClassifier.Result` is a public static value class with `track`, `List<String> signals`, and `boolean explicit` fields. `EligibilityDecision` stores `status` as `PASS`, `FAIL`, or `UNKNOWN` and exposes an immutable reason list.

- [ ] **Step 1: Write failing classifier/filter tests.** Cover internship/co-op, new-grad/early-career/university-hire, generic full-time, explicit senior/staff/lead, Android versus Platform, and an empty location field. Assert explicit mismatches are `FAIL` and missing metadata is `UNKNOWN`.

- [ ] **Step 2: Run the focused tests.**

Run: `mvn --file backend/pom.xml -Dtest=CareerTrackClassifierTest,JobEligibilityFilterTest test`

Expected: FAIL because the classifier, decision object, and filter do not exist.

- [ ] **Step 3: Implement text signal classification.** Check title and description together. Detect internship signals first, then new-grad signals, then full-time metadata. Detect explicit seniority only for rejecting a New Graduate request; do not reject senior/mid roles for General Full-time.

- [ ] **Step 4: Implement tri-state filtering.** Reject explicit track, role-family, specialization, or location mismatches; keep unknown metadata with an `unverified` reason; never inspect skill overlap in this class.

- [ ] **Step 5: Populate `JobRecommendation` eligibility fields.** Add `eligibilityStatus`, `eligibilityReasons`, `detectedCareerTrack`, and `detectedSpecialization` while preserving all existing JSON fields.

- [ ] **Step 6: Run the focused tests again.**

Run: `mvn --file backend/pom.xml -Dtest=CareerTrackClassifierTest,JobEligibilityFilterTest test`

Expected: PASS for all explicit mismatch and unknown metadata cases.

- [ ] **Step 7: Commit eligibility filtering.**

```bash
git add backend/src/main/java/edu/cmu/msis/project4/model/EligibilityDecision.java backend/src/main/java/edu/cmu/msis/project4/service/CareerTrackClassifier.java backend/src/main/java/edu/cmu/msis/project4/service/JobEligibilityFilter.java backend/src/main/java/edu/cmu/msis/project4/model/JobRecommendation.java backend/src/test/java/edu/cmu/msis/project4/service/CareerTrackClassifierTest.java backend/src/test/java/edu/cmu/msis/project4/service/JobEligibilityFilterTest.java
git commit -m "feat: filter jobs by career track and role eligibility"
```

## Task 4: Replace the deterministic score with explainable soft components

**Files:**
- Create: `backend/src/main/java/edu/cmu/msis/project4/model/MatchFeatures.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/service/JobMatchingService.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/model/JobRecommendation.java`
- Modify: `backend/src/test/java/edu/cmu/msis/project4/service/JobMatchingServiceTest.java`

**Interfaces:**
- `JobMatchingService.score(JobRecommendation job, RecommendationRequest request, SearchProfile profile)` returns `MatchFeatures`.
- `MatchFeatures.deterministicScore()` computes `0.40*roleFit + 0.35*requiredSkillFit + 0.15*preferredSkillFit + 0.10*evidenceFit` and rounds to `0..100`.

- [ ] **Step 1: Extend tests with component assertions.** Create one job with matching role family but missing skills, one with matching required and preferred skills, and one with a specialization mismatch. Assert role fit is the largest contributor, skill gaps do not zero a job, and no eligibility field is used as a score component.

- [ ] **Step 2: Run the focused test to capture the current failure.**

Run: `mvn --file backend/pom.xml -Dtest=JobMatchingServiceTest test`

Expected: FAIL on the new component and specialization assertions because the current service returns only one aggregate score.

- [ ] **Step 3: Implement canonical phrase matching.** Load skill aliases, normalize phrases, split JD text into required/preferred sections using headings such as `required`, `minimum qualifications`, `preferred`, and `nice to have`, and produce evidence strings such as `Required skill overlap: Python, SQL`.

- [ ] **Step 4: Implement role and evidence components.** Compute role-family coverage from title plus responsibilities; compute evidence fit from resume project/work phrases overlapping the JD responsibilities. Do not add career track, seniority, or location points.

- [ ] **Step 5: Populate public score fields.** Add `roleFitScore`, `requiredSkillScore`, `preferredSkillScore`, `evidenceFitScore`, `deterministicScore`, and `scoringVersion="hybrid-v1"` to each `JobRecommendation`, and mirror `scoringVersion` in `RecommendationResponse.Meta`; keep `matchScore` equal to the deterministic score until LLM blending runs.

- [ ] **Step 6: Run focused and existing tests.**

Run: `mvn --file backend/pom.xml -Dtest=JobMatchingServiceTest test`

Expected: PASS with all scores in `0..100`, relevant roles ahead of unrelated roles, and reasons containing component evidence.

- [ ] **Step 7: Commit the soft scorer.**

```bash
git add backend/src/main/java/edu/cmu/msis/project4/model/MatchFeatures.java backend/src/main/java/edu/cmu/msis/project4/service/JobMatchingService.java backend/src/main/java/edu/cmu/msis/project4/model/JobRecommendation.java backend/src/main/java/edu/cmu/msis/project4/model/RecommendationResponse.java backend/src/test/java/edu/cmu/msis/project4/service/JobMatchingServiceTest.java
git commit -m "feat: add explainable role and skill scoring"
```

## Task 5: Upgrade the DeepSeek semantic review contract

**Files:**
- Create: `backend/src/main/java/edu/cmu/msis/project4/service/LlmResponseParser.java`
- Create: `backend/src/test/java/edu/cmu/msis/project4/service/LlmResponseParserTest.java`
- Create: `backend/src/test/resources/fixtures/deepseek-matches.json`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/service/LlmReranker.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/model/JobRecommendation.java`

**Interfaces:**
- `LlmResponseParser.parse(String rawJson, List<JobRecommendation> jobs)` returns `List<LlmResponseParser.Result>` of validated per-job semantic results; it clamps scores, truncates text, ignores unknown job keys, and rejects a response with no valid matches.
- `LlmReranker.rerank(List<JobRecommendation> eligibleJobs, RecommendationRequest request, SearchProfile profile)` reviews at most 20 eligible jobs and returns the same list with blended scores.

`LlmResponseParser.Result` contains `jobKey`, `semanticScore`, `rationale`, `transferableSkills`, `missingSkills`, `currentBullet`, `suggestedBullet`, `resumeAdvice`, and `confidence`.

- [ ] **Step 1: Write parser tests.** Cover valid JSON, fenced JSON, missing optional fields, scores below `0`/above `100`, unknown job keys, malformed JSON, and a response that has no valid matches.

- [ ] **Step 2: Run the parser test.**

Run: `mvn --file backend/pom.xml -Dtest=LlmResponseParserTest test`

Expected: FAIL because the parser class and fixture do not exist.

- [ ] **Step 3: Implement the pure parser.** Accept the new `semanticScore` field and the legacy `matchScore` field for compatibility; cap `rationale` at 500 characters, `currentBullet` and `suggestedBullet` at 700 characters, and `resumeAdvice` at 600 characters; mark `llmEvaluated=true` only for a valid matching job.

- [ ] **Step 4: Update the system prompt.** Require JSON only, transferable-skill reasoning, no invented experience, exact/faithful current-bullet excerpts, truthful suggested bullets, two-sentence maximum advice, and no override of the server's eligibility decision.

- [ ] **Step 5: Blend scores and preserve fallback.** Set `matchScore = round(deterministicScore*0.55 + llmScore*0.45)` for parsed matches; leave unmatched jobs at their deterministic score; catch timeout, HTTP, parsing, and provider configuration errors and return the original eligible list.

- [ ] **Step 6: Run parser and Maven tests.**

Run: `mvn --file backend/pom.xml -Dtest=LlmResponseParserTest,JobMatchingServiceTest test`

Expected: PASS; no LLM test requires a real API key.

- [ ] **Step 7: Commit the DeepSeek contract.**

```bash
git add backend/src/main/java/edu/cmu/msis/project4/service/LlmResponseParser.java backend/src/main/java/edu/cmu/msis/project4/service/LlmReranker.java backend/src/main/java/edu/cmu/msis/project4/model/JobRecommendation.java backend/src/test/java/edu/cmu/msis/project4/service/LlmResponseParserTest.java backend/src/test/resources/fixtures/deepseek-matches.json
git commit -m "feat: add DeepSeek semantic match review"
```

## Task 6: Reorder the recommendation pipeline and preserve history behavior

**Files:**
- Modify: `backend/src/main/java/edu/cmu/msis/project4/service/RecommendationService.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/client/SerpApiClient.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/model/RecommendationResponse.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/repository/MongoRepository.java`
- Modify: `backend/src/main/java/edu/cmu/msis/project4/service/DailyDigestService.java`
- Test: `backend/src/test/java/edu/cmu/msis/project4/service/RecommendationPipelineTest.java`

**Interfaces:**
- `RecommendationService` production constructor keeps the no-argument form; a package-private constructor accepts `MongoRepository`, `SerpApiClient`, `LocationResolutionService`, `RoleProfileExpander`, `JobEligibilityFilter`, `JobMatchingService`, and `LlmReranker` for deterministic orchestration tests.
- `SerpApiClient.searchJobs(String queryVariant, String location, CareerTrack careerTrack)` builds one encoded Google Jobs query and never appends a duplicate track token.

- [ ] **Step 1: Write a pipeline test with fakes.** Return synthetic internship, new-grad, senior, Android, and Platform jobs from a fake SerpAPI client. Assert the service filters before ranking, calls the LLM only with eligible top candidates, deduplicates by `jobKey`, and persists only returned unseen jobs.

- [ ] **Step 2: Run the pipeline test.**

Run: `mvn --file backend/pom.xml -Dtest=RecommendationPipelineTest test`

Expected: FAIL because the current service calls ranking before eligibility and constructs dependencies internally.

- [ ] **Step 3: Add dependency injection seams.** Keep the existing no-arg constructor for Tomcat and add the package-private constructor used by tests. Do not change endpoint signatures.

- [ ] **Step 4: Implement retrieval expansion.** For each resolved location, run at most three role query variants, deduplicate immediately, stop once the configured candidate cap is reached, and retain current nationwide state fan-out limits.

- [ ] **Step 5: Implement the ordered pipeline.** Normalize request, expand profile, retrieve, deduplicate, evaluate eligibility, discard only `FAIL`, rank all `PASS`/`UNKNOWN`, send the top 20 eligible jobs to `LlmReranker`, sort blended results, apply history deduplication, and save history only for returned jobs.

- [ ] **Step 6: Add response metadata.** Populate `rawCandidateCount`, `eligibleCount`, `filteredCount`, `llmEvaluatedCount`, `scoringVersion`, `careerTrack`, and the existing search metadata.

- [ ] **Step 7: Persist and load the new digest fields.** Store `careerTrack` and `specialization` in `MongoRepository.savePreference`, read them in `activePreferences`, and map them in `DailyDigestService.toRequest`; fall back to the saved `experienceLevel` alias for older subscriptions. Store the new component scores and bullet-advice fields alongside the existing recommendation history document.

- [ ] **Step 8: Update daily digest selection.** Select up to ten unseen jobs with final score `>=68`; if fewer qualify, send fewer and include the actual count in the email subject/body. Never include `FAIL` jobs.

- [ ] **Step 9: Run focused and full tests.**

Run: `mvn --file backend/pom.xml -Dtest=RecommendationPipelineTest,JobMatchingServiceTest,EmailServiceTest test`

Expected: PASS, including existing history and HTML escaping tests.

- [ ] **Step 10: Commit the pipeline.**

```bash
git add backend/src/main/java/edu/cmu/msis/project4/service/RecommendationService.java backend/src/main/java/edu/cmu/msis/project4/client/SerpApiClient.java backend/src/main/java/edu/cmu/msis/project4/model/RecommendationResponse.java backend/src/main/java/edu/cmu/msis/project4/repository/MongoRepository.java backend/src/main/java/edu/cmu/msis/project4/service/DailyDigestService.java backend/src/test/java/edu/cmu/msis/project4/service/RecommendationPipelineTest.java
git commit -m "feat: orchestrate eligibility before hybrid ranking"
```

## Task 7: Update the browser and API presentation

**Files:**
- Modify: `backend/src/main/webapp/index.html`
- Modify: `android-app/app/src/main/java/ds/edu/cmu/model/RecommendationRequest.java`
- Modify: `android-app/app/src/main/java/ds/edu/cmu/model/RecommendationResponse.java`
- Modify: `android-app/app/src/main/java/ds/edu/cmu/FirstFragment.java`
- Modify: `android-app/app/src/main/java/ds/edu/cmu/JobRecommendationAdapter.java`
- Modify: `android-app/app/src/main/res/layout/fragment_first.xml`
- Modify: `android-app/app/src/main/res/layout/item_job.xml`
- Modify: `android-app/app/src/main/res/values/arrays.xml`
- Modify: `android-app/app/src/main/res/values/strings.xml`

**Interfaces:**
- Browser request body sends `careerTrack` values `INTERNSHIP`, `NEW_GRADUATE`, or `GENERAL_FULL_TIME` and optional `specialization`.
- Response cards render `matchScore`, eligibility status, component reasons, `transferableSkills`, `missingSkills`, `currentBullet`, `suggestedBullet`, and `resumeAdvice` when present.

- [ ] **Step 1: Add browser controls.** Replace the existing `Experience` select with the three career-track labels and values; add an optional specialization input whose placeholder explains `Android, Platform, Backend, or leave blank for generic role`.

- [ ] **Step 2: Update the request builder.** Send `careerTrack` and `specialization`; retain `experienceLevel` only if needed for old Android builds, not in new browser requests.

- [ ] **Step 3: Render explainable results.** Show the final percentage prominently, display `Strong match`, `Recommend`, or `Stretch opportunity`, show eligibility warnings for `UNKNOWN`, and render advice as `Current bullet → Suggested bullet` only when both fields are nonblank.

- [ ] **Step 4: Preserve safe HTML behavior.** Continue escaping all job, reason, skill, and advice strings and continue validating apply links through `safeUrl`.

- [ ] **Step 5: Update Android DTOs and controls.** Add nullable fields so an older APK can parse the expanded response while the new release can send `careerTrack`; update `FirstFragment`, `fragment_first.xml`, `arrays.xml`, and `strings.xml` to show and persist the three career-track choices; do not change the API base URL mechanism.

- [ ] **Step 6: Render native recommendation details.** Update `JobRecommendationAdapter` and `item_job.xml` to display the final percentage, eligibility warning, top reasons, and a concise resume tip while keeping apply links safe.

- [ ] **Step 7: Run backend tests and a static browser check.**

Run: `mvn --file backend/pom.xml test`

Expected: PASS. Open the local WAR or Render URL and verify a generic Software Engineer search, an Android specialization search, and the three track labels render without console errors.

- [ ] **Step 8: Commit presentation changes.**

```bash
git add backend/src/main/webapp/index.html android-app/app/src/main/java/ds/edu/cmu/model/RecommendationRequest.java android-app/app/src/main/java/ds/edu/cmu/model/RecommendationResponse.java android-app/app/src/main/java/ds/edu/cmu/FirstFragment.java android-app/app/src/main/java/ds/edu/cmu/JobRecommendationAdapter.java android-app/app/src/main/res/layout/fragment_first.xml android-app/app/src/main/res/layout/item_job.xml android-app/app/src/main/res/values/arrays.xml android-app/app/src/main/res/values/strings.xml
git commit -m "feat: expose career tracks and resume advice in clients"
```

## Task 8: Configure DeepSeek, Render, and GitHub Actions for cloud execution

**Files:**
- Modify: `.env.example`
- Modify: `render.yaml`
- Modify: `.github/workflows/daily-digest.yml`
- Modify: `README.md`

**Interfaces:**
- Local configuration file: `D:\MISM\Collection\JobLens\.env` remains ignored by Git.
- Render secret variables: `QWEN_API_KEY`, `QWEN_BASE_URL`, `QWEN_MODEL`, `SERPAPI_API_KEY`, `MONGODB_URI`, `RESEND_API_KEY`, `DIGEST_FROM_EMAIL`, and `DIGEST_TRIGGER_TOKEN`.
- GitHub Actions secrets: `JOBLENS_API_URL` and `DIGEST_TRIGGER_TOKEN`.

- [ ] **Step 1: Update `.env.example`.** Document the safe DeepSeek template:

```text
QWEN_API_KEY=
QWEN_BASE_URL=https://api.deepseek.com
QWEN_MODEL=deepseek-flash
```

Keep the key blank and state that the same values must be entered in Render, not committed.

- [ ] **Step 2: Update `render.yaml`.** Add the three QWEN variables as `sync: false` entries so a Blueprint deployment prompts for them; leave `DIGEST_TRIGGER_TOKEN` generated by Render and keep `/api/health` as the health check.

- [ ] **Step 3: Add workflow preflight.** Before curl, fail with a GitHub error annotation if `JOBLENS_API_URL` is empty or does not start with `http://` or `https://`, and fail separately if `DIGEST_TRIGGER_TOKEN` is empty. Normalize one trailing slash without printing either secret.

- [ ] **Step 4: Update README deployment instructions.** Explain the local `.env` path, Render variables, DeepSeek fallback, GitHub secrets, the public Render origin used by the portfolio, and the three career tracks. Do not add a fake public URL.

- [ ] **Step 5: Run configuration checks.**

Run: `git diff --check`

Expected: no whitespace errors; `git status --short` shows only the four intended documentation/config files.

- [ ] **Step 6: Commit cloud configuration.**

```bash
git add .env.example render.yaml .github/workflows/daily-digest.yml README.md
git commit -m "chore: configure DeepSeek and cloud digest deployment"
```

## Task 9: Full verification and deployment handoff

**Files:**
- Verify: all files changed by Tasks 1–8
- Verify: `Dockerfile`, `render.yaml`, `.github/workflows/daily-digest.yml`

- [ ] **Step 1: Run the full backend test suite.**

Run: `mvn --file backend/pom.xml clean test`

Expected: PASS with no test requiring external credentials.

- [ ] **Step 2: Build the production image.**

Run: `docker build -t joblens:hybrid-matching .`

Expected: successful WAR build and Tomcat image creation.

- [ ] **Step 3: Run a local health smoke test.** Start the image with non-secret test values for `SERPAPI_API_KEY` and `MONGODB_URI` only if the endpoint requires startup configuration, then request `GET /api/health`.

Expected: HTTP `200` from `/api/health`; no API key is printed.

- [ ] **Step 4: Validate the cloud checklist in Render.** Enter the real MongoDB Atlas, SerpAPI, Resend, Digest token, and DeepSeek values in Render Environment; deploy the Blueprint; confirm the deployed service origin plus `/api/health` returns HTTP `200` and the browser UI loads from that same origin.

- [ ] **Step 5: Validate GitHub Actions without exposing secrets.** Set `JOBLENS_API_URL` to the Render origin and `DIGEST_TRIGGER_TOKEN` to the exact Render value; use `workflow_dispatch`; confirm the job reaches the endpoint and does not exit with curl code `3`.

- [ ] **Step 6: Run three cloud searches.** Test `Data Scientist + New Graduate`, `Software Engineer + General Full-time`, and `Android Software Engineer + Internship`; confirm hard mismatches are absent, scores are present, and advice is specific to the uploaded resume.

- [ ] **Step 7: Record the final public origin.** Add the verified Render URL to the portfolio site and Android release build configuration; do not place DeepSeek, MongoDB, SerpAPI, Resend, or digest tokens in frontend code.

- [ ] **Step 8: Commit verification metadata only if needed.** Do not commit secrets or generated build artifacts. If README needs the verified public origin, commit only that URL in a separate documentation commit.

---

## Plan self-review

- Spec coverage: career-track semantics, hard eligibility, role specialization, soft score, DeepSeek contract, fallback behavior, UI, cloud configuration, and acceptance criteria each map to Tasks 1–9.
- Placeholder scan: no `TODO`, `TBD`, or unspecified implementation step is used; commands, file paths, field names, and expected outcomes are explicit.
- Type consistency: `CareerTrack`, `SearchProfile`, `EligibilityDecision`, `MatchFeatures`, `JobEligibilityFilter`, and `LlmResponseParser` are introduced before later tasks consume them; legacy `experienceLevel` remains a string compatibility input.
- Scope: no portfolio-site implementation, authentication redesign, payment system, or Apple distribution work is included; this plan only makes JobLens cloud-ready and matching behavior coherent.
