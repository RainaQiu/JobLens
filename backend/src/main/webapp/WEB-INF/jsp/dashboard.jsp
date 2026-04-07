<%@ page import="java.util.List" %>
<%@ page import="org.bson.Document" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>JobLens Dashboard</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 24px; }
        table { border-collapse: collapse; width: 100%; margin-top: 12px; }
        th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
        th { background: #f4f4f4; }
        .cards { display: flex; gap: 12px; }
        .card { border: 1px solid #ddd; padding: 12px; min-width: 180px; }
    </style>
</head>
<body>
<h1>JobLens Operations Dashboard</h1>

<%
    List<Document> logs = (List<Document>) request.getAttribute("logs");
    int totalRequests = (int) request.getAttribute("totalRequests");
    long successCount = (long) request.getAttribute("successCount");
    long failureCount = (long) request.getAttribute("failureCount");
    long avgLatency = (long) request.getAttribute("averageApiLatencyMs");
    long avgReturnedCount = (long) request.getAttribute("averageReturnedCount");
    String topRole = (String) request.getAttribute("topRole");
%>

<div class="cards">
    <div class="card">
        <strong>Total Requests</strong>
        <div><%= totalRequests %></div>
    </div>
    <div class="card">
        <strong>Successful Requests</strong>
        <div><%= successCount %></div>
    </div>
    <div class="card">
        <strong>Failed Requests</strong>
        <div><%= failureCount %></div>
    </div>
    <div class="card">
        <strong>Average API Latency (ms)</strong>
        <div><%= avgLatency %></div>
    </div>
    <div class="card">
        <strong>Average Jobs Returned</strong>
        <div><%= avgReturnedCount %></div>
    </div>
    <div class="card">
        <strong>Top Search Role</strong>
        <div><%= topRole %></div>
    </div>
</div>

<h2>Recent Logs</h2>
<table>
    <thead>
    <tr>
        <th>Timestamp</th>
        <th>Endpoint</th>
        <th>User ID</th>
        <th>Device</th>
        <th>Role</th>
        <th>Location</th>
        <th>Exp Level</th>
        <th>Third-party Status</th>
        <th>API Latency</th>
        <th>Returned Count</th>
        <th>Error Type</th>
    </tr>
    </thead>
    <tbody>
    <% if (logs.isEmpty()) { %>
        <tr>
            <td colspan="11">No mobile request logs have been stored yet.</td>
        </tr>
    <% } %>
    <% for (Document log : logs) { %>
        <tr>
            <td><%= log.getString("timestamp") %></td>
            <td><%= log.getString("endpoint") %></td>
            <td><%= log.getString("userId") %></td>
            <td><%= log.getString("deviceModel") %></td>
            <td><%= log.getString("inputRole") %></td>
            <td><%= log.getString("inputLocation") %></td>
            <td><%= log.getString("inputExperienceLevel") %></td>
            <td><%= log.get("thirdPartyStatus") %></td>
            <td><%= log.get("thirdPartyLatencyMs") %></td>
            <td><%= log.get("returnedCount") %></td>
            <td><%= log.getString("errorType") %></td>
        </tr>
    <% } %>
    </tbody>
</table>
</body>
</html>
