<%@ page import="java.util.List" %>
<%@ page import="org.bson.Document" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>JobLens Dashboard</title>
    <style>
        :root {
            color-scheme: light;
            --ink: #16333c;
            --muted: #607177;
            --accent: #2f7f79;
            --accent-soft: #dff1eb;
            --surface: #fffdf9;
            --canvas: #f4efe7;
            --stroke: #d4ddd9;
        }
        body {
            font-family: "Segoe UI", Arial, sans-serif;
            margin: 0;
            background: var(--canvas);
            color: var(--ink);
        }
        .page {
            max-width: 1400px;
            margin: 0 auto;
            padding: 32px 24px 48px;
        }
        .hero {
            margin-bottom: 24px;
        }
        .hero h1 {
            margin: 0 0 8px;
            font-size: 32px;
        }
        .hero p {
            margin: 0;
            color: var(--muted);
        }
        .cards {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 12px;
            margin-bottom: 24px;
        }
        .card {
            background: var(--surface);
            border: 1px solid var(--stroke);
            border-radius: 16px;
            padding: 16px;
            box-shadow: 0 8px 24px rgba(22, 51, 60, 0.06);
        }
        .card strong {
            display: block;
            color: var(--muted);
            margin-bottom: 10px;
            font-size: 13px;
            text-transform: uppercase;
            letter-spacing: 0.04em;
        }
        .card div {
            font-size: 24px;
            font-weight: 700;
        }
        .table-shell {
            background: var(--surface);
            border: 1px solid var(--stroke);
            border-radius: 18px;
            overflow: hidden;
            box-shadow: 0 10px 28px rgba(22, 51, 60, 0.06);
        }
        h2 {
            margin: 28px 0 14px;
            font-size: 22px;
        }
        table {
            border-collapse: collapse;
            width: 100%;
        }
        th, td {
            border-bottom: 1px solid #e8ece9;
            padding: 12px 14px;
            text-align: left;
            vertical-align: top;
            font-size: 14px;
        }
        th {
            background: #eef5f2;
            color: var(--ink);
            font-size: 13px;
            text-transform: uppercase;
            letter-spacing: 0.03em;
        }
        td small {
            color: var(--muted);
        }
        .pill {
            display: inline-block;
            padding: 4px 10px;
            border-radius: 999px;
            background: var(--accent-soft);
            color: var(--accent);
            font-size: 12px;
            font-weight: 600;
        }
    </style>
</head>
<body>
<%
    List<Document> logs = (List<Document>) request.getAttribute("logs");
    int totalRequests = (int) request.getAttribute("totalRequests");
    long successCount = (long) request.getAttribute("successCount");
    long failureCount = (long) request.getAttribute("failureCount");
    long avgLatency = (long) request.getAttribute("averageApiLatencyMs");
    long avgReturnedCount = (long) request.getAttribute("averageReturnedCount");
    String topRole = (String) request.getAttribute("topRole");
    long applyCoveragePercent = (long) request.getAttribute("applyCoveragePercent");
    long nationwideRequestCount = (long) request.getAttribute("nationwideRequestCount");
    String topResolvedLocation = (String) request.getAttribute("topResolvedLocation");
%>

<div class="page">
    <div class="hero">
        <h1>JobLens Operations Dashboard</h1>
        <p>Tracks mobile search activity, SerpAPI usage, U.S. nationwide expansion, and MongoDB-backed job history.</p>
    </div>

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
            <strong>Average API Latency</strong>
            <div><%= avgLatency %> ms</div>
        </div>
        <div class="card">
            <strong>Average Jobs Returned</strong>
            <div><%= avgReturnedCount %></div>
        </div>
        <div class="card">
            <strong>Top Search Role</strong>
            <div><%= topRole %></div>
        </div>
        <div class="card">
            <strong>Apply Link Coverage</strong>
            <div><%= applyCoveragePercent %>%</div>
        </div>
        <div class="card">
            <strong>Nationwide U.S. Requests</strong>
            <div><%= nationwideRequestCount %></div>
        </div>
        <div class="card">
            <strong>Top Resolved Location</strong>
            <div><%= topResolvedLocation %></div>
        </div>
    </div>

    <h2>Recent Mobile Logs</h2>
    <div class="table-shell">
        <table>
            <thead>
            <tr>
                <th>Timestamp</th>
                <th>User</th>
                <th>Role</th>
                <th>Raw Input</th>
                <th>Resolved Search</th>
                <th>Scope / Strategy</th>
                <th>Device</th>
                <th>3rd-party Calls</th>
                <th>Latency</th>
                <th>Returned</th>
                <th>Apply Links</th>
                <th>Error</th>
            </tr>
            </thead>
            <tbody>
            <% if (logs.isEmpty()) { %>
                <tr>
                    <td colspan="12">No mobile request logs have been stored yet.</td>
                </tr>
            <% } %>
            <% for (Document log : logs) { %>
                <tr>
                    <td><%= log.getString("timestamp") %></td>
                    <td>
                        <strong><%= log.getString("userId") %></strong><br/>
                        <small><%= log.getString("appVersion") %></small>
                    </td>
                    <td><%= log.getString("inputRole") %></td>
                    <td>
                        <%= log.getString("inputLocation") %><br/>
                        <small><%= log.getString("inputExperienceLevel") %></small>
                    </td>
                    <td>
                        <strong><%= log.getString("resolvedLocation") %></strong><br/>
                        <small><%= log.getString("searchedLocations") %></small>
                    </td>
                    <td>
                        <span class="pill"><%= log.getString("inputSearchScope") %></span><br/><br/>
                        <small><%= log.getString("searchStrategy") %></small>
                    </td>
                    <td>
                        <%= log.getString("deviceModel") %><br/>
                        <small><%= log.getString("clientIp") %></small>
                    </td>
                    <td>
                        status <%= log.get("thirdPartyStatus") %><br/>
                        <small><%= log.get("thirdPartyCallCount") %> call(s)</small>
                    </td>
                    <td><%= log.get("thirdPartyLatencyMs") %> ms</td>
                    <td>
                        <%= log.get("returnedCount") %><br/>
                        <small><%= log.get("searchedLocationsCount") %> location(s)</small>
                    </td>
                    <td><%= log.get("jobsWithApplyLinks") %></td>
                    <td><%= log.getString("errorType") %></td>
                </tr>
            <% } %>
            </tbody>
        </table>
    </div>
</div>
</body>
</html>
