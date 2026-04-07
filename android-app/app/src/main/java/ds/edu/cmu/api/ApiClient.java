package ds.edu.cmu.api;

import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import ds.edu.cmu.BuildConfig;
import ds.edu.cmu.model.HistoryItem;
import ds.edu.cmu.model.RecommendationRequest;
import ds.edu.cmu.model.RecommendationResponse;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Author: Raina Qiu (yuluq)
 * Replace BuildConfig.API_BASE_URL when you switch from local Tomcat to Codespaces.
 */
public class ApiClient {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final Type HISTORY_LIST_TYPE = new TypeToken<ArrayList<HistoryItem>>() { }.getType();

    private final Gson gson = new Gson();
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

    public void fetchRecommendations(RecommendationRequest request, ApiCallback<RecommendationResponse> callback) {
        String jsonBody = gson.toJson(request);
        Request httpRequest = baseRequestBuilder(BuildConfig.API_BASE_URL + "/api/recommendations")
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build();

        enqueue(httpRequest, RecommendationResponse.class, callback);
    }

    public void fetchHistory(String userId, ApiCallback<List<HistoryItem>> callback) {
        HttpUrl parsedBaseUrl = HttpUrl.parse(BuildConfig.API_BASE_URL + "/api/history");
        if (parsedBaseUrl == null) {
            callback.onError("API base URL is invalid. Update BuildConfig.API_BASE_URL before running.");
            return;
        }

        HttpUrl url = parsedBaseUrl.newBuilder()
                .addQueryParameter("userId", userId)
                .build();

        Request httpRequest = baseRequestBuilder(url.toString()).get().build();
        enqueue(httpRequest, HISTORY_LIST_TYPE, callback);
    }

    private Request.Builder baseRequestBuilder(String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader("X-Device-Model", deviceModel())
                .addHeader("X-App-Version", BuildConfig.VERSION_NAME);
    }

    private <T> void enqueue(Request request, Class<T> responseType, ApiCallback<T> callback) {
        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                callback.onError(errorMessageFor(e));
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                try (Response safeResponse = response) {
                    String body = safeResponse.body() == null ? "" : safeResponse.body().string();
                    if (!safeResponse.isSuccessful()) {
                        callback.onError(extractServerMessage(body, safeResponse.code()));
                        return;
                    }
                    callback.onSuccess(gson.fromJson(body, responseType));
                } catch (RuntimeException e) {
                    callback.onError("The app could not parse the server response.");
                }
            }
        });
    }

    private <T> void enqueue(Request request, Type responseType, ApiCallback<T> callback) {
        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                callback.onError(errorMessageFor(e));
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws IOException {
                try (Response safeResponse = response) {
                    String body = safeResponse.body() == null ? "" : safeResponse.body().string();
                    if (!safeResponse.isSuccessful()) {
                        callback.onError(extractServerMessage(body, safeResponse.code()));
                        return;
                    }
                    callback.onSuccess(gson.fromJson(body, responseType));
                } catch (RuntimeException e) {
                    callback.onError("The app could not parse the server response.");
                }
            }
        });
    }

    private String extractServerMessage(String body, int statusCode) {
        try {
            ApiError error = gson.fromJson(body, ApiError.class);
            if (error != null && error.error != null && !error.error.trim().isEmpty()) {
                return error.error;
            }
        } catch (RuntimeException ignored) {
            // Fall back to a generic status-based message.
        }
        return "Server request failed with status " + statusCode + ".";
    }

    private String errorMessageFor(IOException exception) {
        if (exception instanceof SocketTimeoutException) {
            return "The request timed out. Please try again.";
        }
        return "Network error. Make sure the backend is reachable at " + BuildConfig.API_BASE_URL + ".";
    }

    private String deviceModel() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);

        void onError(String message);
    }

    private static class ApiError {
        String error;
    }
}
