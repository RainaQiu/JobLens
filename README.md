# JobLens AI

JobLens AI is a mobile-to-cloud distributed application for personalized job discovery. The Android app collects a user's role, location, experience level, and search scope, then calls a Java Servlet backend that uses SerpAPI Google Jobs, stores history and operational logs in MongoDB Atlas, and exposes a dashboard for analytics.

## Author
- Name: Raina Qiu
- Andrew ID: yuluq

## Current MVP (v1.1)
- Android search UI supports country, state, or city input.
- Users can choose `Auto match`, `Specific place`, or `Nationwide U.S.` search scope.
- The backend resolves free-form location text before searching.
- If the request is `United States` or `Nationwide U.S.`, the backend expands the search into a prioritized multi-state U.S. search.
- Results focus on concise, job-seeker-friendly cards: title, company, location, posted time, work mode, employment type, and a direct `Apply Now` action when available.
- Search and history both use client-side pagination (10 items per page) with previous/next controls.
- Recommendation history and mobile request logs are stored in MongoDB Atlas.
- The dashboard shows analytics and formatted logs for mobile search activity.

## Why the location logic matters
Many official career sites force users to choose a specific state, even when the user really wants to stay anywhere in the United States. JobLens AI addresses that gap with two behaviors:

- Specific searches: city/state/country text is normalized and used as a single search location.
- Nationwide U.S. searches: the backend expands a U.S.-wide request into multiple state-level searches, merges the results, removes duplicates, and returns the best fresh jobs first.

This keeps the mobile UI simple while letting the backend own the business logic.

## Core recommendation rules
- Freshness: only jobs posted in the last 7 days are eligible.
- Deduplication: jobs already recommended to the same user are not shown again.
- Location resolution: user input is normalized into a better search location when possible.
- U.S. nationwide expansion: a broad U.S. search fans out into multiple state-level searches to improve geographic coverage.
- Apply-first output: the backend prefers `apply_options` links from SerpAPI and falls back to a share page only when a direct apply link is unavailable.
- Mobile pagination: the app displays search and history results in pages of 10 jobs.

## Tech stack
- Android Studio with Java Android client
- Java 17
- Java Servlets + JSP
- Maven
- MongoDB Atlas with `mongodb-driver-sync`
- SerpAPI Google Jobs API
- Tomcat 9 for local Servlet testing

## Project structure
- `backend/`
  - `controller/` for servlets and dashboard routing
  - `service/` for recommendation and location resolution logic
  - `client/` for SerpAPI integration
  - `repository/` for MongoDB reads and writes
  - `model/` for request/response DTOs
  - `src/main/webapp/WEB-INF/jsp/` for the dashboard JSP
- `android-app/`
  - native Android client
  - RecyclerView-based results and history views
- `.env.example`
  - environment variable template
- `docs/`
  - screenshots, writeup, and submission materials

## API endpoints
- `POST /api/recommendations`
  - Request JSON:
    - `userId`
    - `role`
    - `location`
    - `experienceLevel`
    - `searchScope`
  - Response JSON:
    - `requestId`
    - `recommendedCount`
    - `jobs`
    - `meta`
- `GET /api/history?userId=...`
  - Returns saved recommendations for one user
- `GET /dashboard`
  - Shows operations analytics and formatted logs

## Data flow
1. Android sends a recommendation request to the backend.
2. The backend validates the request and resolves the location strategy.
3. The backend calls SerpAPI Google Jobs one or more times.
4. The backend filters for fresh jobs, merges results, and removes duplicates.
5. Newly recommended jobs are written to MongoDB history.
6. Request metadata and third-party API metrics are written to MongoDB logs.
7. The backend returns concise JSON tailored for the Android UI.
8. The dashboard reads MongoDB and displays analytics plus full formatted logs.

## MongoDB usage
MongoDB Atlas is used for persistent storage across restarts.

Currently active collections:
- `recommendation_history`
  - stores the jobs already recommended to each user
- `request_logs`
  - stores mobile request metadata, resolved locations, third-party latency, and result counts

The backend reads MongoDB configuration from real environment variables. It does not auto-load `.env`.

## Required environment variables
See `.env.example`.

Important values:
- `SERPAPI_API_KEY`
- `MONGODB_URI`
- `MAX_RESULTS_RETURNED`
- `NATIONWIDE_US_MAX_STATES`
- `NATIONWIDE_US_MIN_STATES`

Notes:
- Keep real secrets out of source control.
- Set variables in your IntelliJ / Tomcat run configuration or in Codespaces.
- `NATIONWIDE_US_MAX_STATES` controls how many prioritized U.S. state searches are used for a nationwide request.
- `NATIONWIDE_US_MIN_STATES` controls the minimum number of state searches before early exit is allowed.
- `MAX_RESULTS_RETURNED` defaults to `50`, so the app can paginate larger recommendation sets.

## Local development
- Open `backend/` in IntelliJ IDEA.
- Run the backend on Tomcat 9 with environment variables configured.
- Open `android-app/` in Android Studio.
- For the Android emulator, the app currently targets `http://10.0.2.2:7999`.
- If you deploy to Codespaces, replace `BuildConfig.API_BASE_URL` with the public forwarded HTTPS URL.

## Notes for this course project
- The backend uses Servlets, not JAX-RS.
- The dashboard is intended for desktop/laptop browsers.
- All important mobile requests are logged to MongoDB for analytics and grading visibility.
- The project keeps business logic on the server so the Android app stays thin and focused on input/output.

