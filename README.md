# JobLens AI

Mobile-to-cloud distributed application for weekly personalized job recommendations.

## Author
- Name: Raina Qiu
- Andrew ID: yuluq

## What this project does
- Android app collects user preferences (role, location, experience level).
- Cloud Servlet web service fetches jobs from SerpAPI Google Jobs API.
- Backend filters to only recent jobs (last 7 days), removes jobs already recommended to the same user, and returns concise results.
- Backend stores recommendation history and request logs in MongoDB Atlas.
- Dashboard endpoint shows operations analytics and formatted logs.

## Scope boundaries
- In scope:
  - Native Android app UI + API calls
  - Java Servlet backend (no JAX-RS)
  - SerpAPI integration
  - MongoDB Atlas persistence
  - Weekly scheduled recommendation generation (Monday)
  - Dashboard analytics + logs
- Out of scope:
  - Full account authentication system
  - Push notifications
  - ML training pipeline

## Core recommendation rules
- Freshness: only jobs posted in the last 7 days are eligible.
- De-duplication: if a job was already recommended to a user, skip it.
- History: every newly recommended job is stored and can be viewed by user later.
- Weekly schedule: every Monday the backend runs recommendation generation for active user preferences.

## Tech stack
- Android Studio (`studio64.exe`), Android app module
- Java 17
- Java Servlets + JSP
- Maven
- MongoDB Atlas (`mongodb-driver-sync`)
- External API: SerpAPI Google Jobs API

## Project structure
- `backend/` - cloud web service and dashboard
  - `controller/` servlets (API + dashboard)
  - `service/` business logic
  - `client/` third-party API integration
  - `repository/` MongoDB access
  - `model/` DTOs/domain objects
  - `src/main/webapp/WEB-INF/jsp/` dashboard JSP
- `android-app/` - Android Studio project for the native mobile client
- `.env.example` - local config placeholders (do not commit real secrets)
- `docs/` - writeups and screenshots

## Data flow
1. Android sends `POST /api/recommendations` with user preferences.
2. Backend validates input.
3. Backend requests SerpAPI Google Jobs data.
4. Backend applies:
   - freshness filter (7 days)
   - duplicate suppression using recommendation history
5. Backend stores logs and newly recommended jobs in MongoDB.
6. Backend returns concise JSON to Android.
7. Dashboard reads MongoDB and displays analytics + logs.

## API endpoints (initial)
- `POST /api/recommendations` - request new recommendations
- `GET /api/history?userId=...` - fetch previously recommended jobs
- `GET /dashboard` - operations dashboard

## Required environment variables
See `.env.example`.

Important:
- The backend does not auto-load `.env`.
- Set `SERPAPI_API_KEY` and `MONGODB_URI` in your IntelliJ / Tomcat run configuration or Codespaces environment.
- Keep real secrets out of source control.

## Local notes
- Open `backend/` in IntelliJ IDEA for the Servlet web service.
- Open `android-app/` in Android Studio for the mobile app.
- Keep keys and Mongo URI in environment variables, never hardcoded.
- For Android emulator, the app defaults to `http://10.0.2.2:8080`.
- For Codespaces deployment, update the Android base URL from `BuildConfig.API_BASE_URL` to the public forwarded URL.

