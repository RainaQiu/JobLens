# Android App

This folder now contains the Android Studio project for JobLens AI.

## Screens
- Search screen:
  - user ID input
  - role input
  - location input
  - experience level spinner
  - search button
  - recommendation results list
- History screen:
  - saved recommendation history for the last searched user ID

## API base URL
- Local backend on emulator: `http://10.0.2.2:8080`
- Codespaces backend: replace `BuildConfig.API_BASE_URL` with your public forwarded URL

## Endpoints used
- `POST /api/recommendations`
- `GET /api/history?userId=...`
