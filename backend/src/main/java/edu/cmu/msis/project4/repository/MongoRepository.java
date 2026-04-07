package edu.cmu.msis.project4.repository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import edu.cmu.msis.project4.config.AppConfig;
import edu.cmu.msis.project4.model.JobRecommendation;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;

/**
 * Author: Raina Qiu (yuluq)
 */
public class MongoRepository {
    private final MongoClient client;
    private final MongoDatabase db;
    private final MongoCollection<Document> historyCollection;
    private final MongoCollection<Document> logCollection;

    public MongoRepository() {
        String uri = AppConfig.getRequired("MONGODB_URI");
        this.client = MongoClients.create(uri);
        this.db = client.getDatabase(AppConfig.get("MONGODB_DB_NAME", "joblens"));
        this.historyCollection = db.getCollection(AppConfig.get("MONGODB_COLLECTION_HISTORY", "recommendation_history"));
        this.logCollection = db.getCollection(AppConfig.get("MONGODB_COLLECTION_LOGS", "request_logs"));
    }

    public boolean alreadyRecommended(String userId, String jobKey) {
        return historyCollection.find(and(eq("userId", userId), eq("jobKey", jobKey))).first() != null;
    }

    public void saveRecommendationHistory(String userId, JobRecommendation job) {
        Document doc = new Document("userId", userId)
                .append("jobKey", job.jobKey)
                .append("title", job.title)
                .append("company", job.company)
                .append("location", job.location)
                .append("postedAt", job.postedAt)
                .append("applyLink", job.applyLink)
                .append("recommendedAt", Instant.now().toString())
                .append("source", "google_jobs");
        historyCollection.insertOne(doc);
    }

    public List<Document> getHistory(String userId) {
        return historyCollection.find(eq("userId", userId))
                .sort(descending("recommendedAt"))
                .into(new ArrayList<>());
    }

    public void saveLog(Document logDoc) {
        logCollection.insertOne(logDoc.append("timestamp", Instant.now().toString()));
    }

    public List<Document> recentLogs(int limit) {
        return logCollection.find()
                .sort(descending("timestamp"))
                .limit(limit)
                .into(new ArrayList<>());
    }
}
