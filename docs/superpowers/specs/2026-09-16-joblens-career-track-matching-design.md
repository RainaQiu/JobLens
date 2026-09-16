# JobLens Career-Track and Hybrid Matching Design

**Status:** Design approved in conversation on 2026-09-16; implementation pending plan review.

## Goal

Make JobLens recommend jobs using a clear two-stage decision: first enforce the user's selected career track, target role, and location as eligibility constraints; then rank eligible jobs by functional fit, required skills, preferred skills, evidence, and transferable experience. For every LLM-reviewed job, return a concise, truthful resume-edit suggestion tied to an existing resume bullet.

## User-facing search semantics

The search form exposes three career tracks:

- `Internship`
- `New Graduate / Early Career`
- `General Full-time`

`New Graduate / Early Career` is a recruiting track for students and recent graduates, not a conventional seniority level. A job may have `employmentType=Full-time` and still belong to the new-graduate track, so JobLens must classify both the SerpAPI employment metadata and title/description text.

The existing `experienceLevel` request field remains accepted for backward compatibility. The server normalizes it to `careerTrack`: `Internship` maps to `INTERNSHIP`, `Entry level` maps to `NEW_GRADUATE`, and `Any` maps to `ANY`. New browser and Android requests use `careerTrack` directly.

Daily subscriptions persist `careerTrack` and optional `specialization` in `UserPreference`; older MongoDB documents that only contain `experienceLevel` are read through the same normalization aliases.

The selected `role` remains a required target. A role taxonomy distinguishes a broad family from a specialization. For example, generic `Software Engineer` accepts Backend, Platform, Android, iOS, and Full Stack jobs; `Android Software Engineer` rejects Platform and iOS jobs unless the job text explicitly shows Android responsibilities.

## Eligibility stage

`JobEligibilityFilter` evaluates each fresh, deduplicated SerpAPI result before scoring. It produces `PASS`, `FAIL`, or `UNKNOWN` plus human-readable reasons.

- Explicit internship/co-op signals pass only `INTERNSHIP`; explicit new-grad signals pass only `NEW_GRADUATE`; a normal full-time job with no internship/new-grad signal passes `GENERAL_FULL_TIME`.
- Explicit `senior`, `staff`, `lead`, or `manager` signals fail a `NEW_GRADUATE` request.
- A role-family mismatch fails. A specialization mismatch fails only when the user specified a specialization; generic role searches keep compatible specializations.
- Location is evaluated against `LocationResolutionService`'s resolved location and search scope. Explicitly incompatible locations fail. Missing or ambiguous SerpAPI location metadata is `UNKNOWN`, not an automatic failure.
- Skill gaps never fail eligibility. They affect soft ranking and the resume advice.

Unknown jobs remain eligible but carry an `unverified` badge and are ranked below equivalent verified jobs. This prevents SerpAPI metadata omissions from silently eliminating good jobs.

## Ranking stage

Only eligible jobs are ranked. The deterministic score uses four soft dimensions and excludes career track, seniority, and location because those are already eligibility decisions:

```text
deterministicScore =
    roleFitScore          * 0.40
  + requiredSkillScore    * 0.35
  + preferredSkillScore   * 0.15
  + evidenceFitScore      * 0.10
```

Each component is an integer in `0..100` and has an evidence list. Role and skill matching use canonical aliases from versioned JSON resources, so `Google Cloud BigQuery` and `BigQuery` resolve to the same skill. Required versus preferred skill sections are identified by JD headings and requirement phrases; absent section markers are treated as preferred rather than assumed required.

The deterministic ranker sends at most 20 eligible candidates to DeepSeek. If DeepSeek returns valid results, the final score is:

```text
finalScore = round(deterministicScore * 0.55 + llmScore * 0.45)
```

If the provider is disabled, times out, returns a non-2xx response, or returns invalid JSON, `finalScore` equals `deterministicScore` and the request still succeeds.

Initial recommendation bands are `80..100 Strong match`, `68..79 Recommend`, `55..67 Stretch opportunity`, and `<55 Not recommended`. The API returns only eligible jobs; the daily digest sends up to ten jobs at or above `68`, and sends fewer rather than padding with low-quality or failed-eligibility jobs.

## DeepSeek contract

The configured OpenAI-compatible provider is DeepSeek Flash using `QWEN_BASE_URL=https://api.deepseek.com` and `QWEN_MODEL=deepseek-flash`. The adapter keeps the existing `QWEN_*` names for compatibility with the Qwen/Gemini implementation.

The prompt tells the model not to override eligibility, invent experience, or recommend a skill without evidence. The model returns JSON with one item per reviewed job:

```json
{
  "jobKey": "...",
  "semanticScore": 0,
  "rationale": "one concise sentence",
  "transferableSkills": ["..."],
  "missingSkills": ["..."],
  "currentBullet": "exact or faithful excerpt from the resume",
  "suggestedBullet": "truthful rewrite only when supported by the resume",
  "resumeAdvice": "two concise sentences maximum",
  "confidence": 0.0
}
```

The UI shows the final percentage, reasons, transferable/missing skills, and the concise advice. The service stores no new resume data beyond the existing subscription behavior.

## Cloud and portfolio behavior

Render remains the single public origin for the Java WAR, browser UI, and API. GitHub Actions invokes the protected Render endpoint for daily digests. The Render Blueprint declares the DeepSeek variables as optional secret inputs. GitHub Actions validates `JOBLENS_API_URL` and `DIGEST_TRIGGER_TOKEN` without printing secrets, then calls `/api/digests/run`.

## Acceptance criteria

1. A New Graduate request cannot return an explicitly senior/staff/lead/manager job.
2. A role specialization mismatch is excluded when the user selected a specialization, while generic Software Engineer searches keep compatible specializations.
3. Explicitly wrong career track or location is excluded before soft scoring; missing SerpAPI metadata is marked `UNKNOWN`.
4. Required skill gaps lower the score but never cause an otherwise eligible job to disappear.
5. DeepSeek adds transferable-skill reasoning and a concrete truthful bullet suggestion; provider failure never turns a search into a server error.
6. Every returned job has a `0..100` final score, reasons, eligibility status, and scoring version.
7. Render health, Docker build, Maven tests, and the GitHub digest preflight all pass before the cloud URL is added to the portfolio.
