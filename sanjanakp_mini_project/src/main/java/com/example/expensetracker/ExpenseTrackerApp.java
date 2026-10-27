package com.example.expensetracker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public final class ExpenseTrackerApp {
    private static final String JSON = "application/json; charset=utf-8";
    private ExpenseTrackerApp() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/api/expenses", ExpenseTrackerApp::handleExpenses);
        server.createContext("/", ExpenseTrackerApp::handleStatic);
        server.start();
        System.out.println("Expense Tracker is running at http://127.0.0.1:" + port);
    }

    private static void handleExpenses(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("GET".equals(method) && "/api/expenses".equals(path)) {
                send(exchange, 200, JSON, listExpenses());
            } else if ("POST".equals(method) && "/api/expenses".equals(path)) {
                int id = insertExpense(readForm(exchange));
                send(exchange, 201, JSON, "{\"id\":" + id + "}");
            } else if (("PUT".equals(method) || "DELETE".equals(method)) && path.matches("/api/expenses/[0-9]+")) {
                int id = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                int changed = "PUT".equals(method) ? updateExpense(id, readForm(exchange)) : deleteExpense(id);
                if (changed == 0) sendError(exchange, 404, "Expense not found.");
                else send(exchange, 200, JSON, "{\"success\":true}");
            } else sendError(exchange, 404, "Endpoint not found.");
        } catch (IllegalArgumentException ex) {
            sendError(exchange, 400, ex.getMessage());
        } catch (SQLException ex) {
            ex.printStackTrace();
            sendError(exchange, 500, "Database error. Check MySQL and the connection settings.");
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(exchange, 500, "The request could not be completed.");
        } finally {
            exchange.close();
        }
    }

    private static String listExpenses() throws SQLException {
        StringBuilder json = new StringBuilder("[");
        try (Connection c = Database.connect();
             PreparedStatement p = c.prepareStatement("SELECT id,title,category,amount,expense_date,notes FROM expenses ORDER BY expense_date DESC,id DESC");
             ResultSet r = p.executeQuery()) {
            boolean first = true;
            while (r.next()) {
                if (!first) json.append(',');
                first = false;
                json.append("{\"id\":").append(r.getInt("id"))
                    .append(",\"title\":").append(jsonString(r.getString("title")))
                    .append(",\"category\":").append(jsonString(r.getString("category")))
                    .append(",\"amount\":").append(r.getBigDecimal("amount").toPlainString())
                    .append(",\"date\":").append(jsonString(r.getDate("expense_date").toString()))
                    .append(",\"notes\":").append(jsonNullable(r.getString("notes"))).append('}');
            }
        }
        return json.append(']').toString();
    }

    private static int insertExpense(Form f) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement p = c.prepareStatement("INSERT INTO expenses(title,category,amount,expense_date,notes) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            bindExpense(p, f);
            p.executeUpdate();
            try (ResultSet keys = p.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("MySQL did not return the new expense ID.");
            }
        }
    }

    private static int updateExpense(int id, Form f) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement p = c.prepareStatement("UPDATE expenses SET title=?,category=?,amount=?,expense_date=?,notes=? WHERE id=?")) {
            bindExpense(p, f);
            p.setInt(6, id);
            return p.executeUpdate();
        }
    }

    private static int deleteExpense(int id) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement("DELETE FROM expenses WHERE id=?")) {
            p.setInt(1, id);
            return p.executeUpdate();
        }
    }

    private static void bindExpense(PreparedStatement p, Form f) throws SQLException {
        String title = f.value("title").trim(), category = f.value("category").trim();
        if (title.isEmpty()) throw new IllegalArgumentException("Enter an expense name.");
        if (title.length() > 100) throw new IllegalArgumentException("Expense name must be 100 characters or fewer.");
        if (category.isEmpty()) throw new IllegalArgumentException("Choose a category.");
        if (category.length() > 50) throw new IllegalArgumentException("Category must be 50 characters or fewer.");
        BigDecimal amount;
        try { amount = new BigDecimal(f.value("amount").trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a valid amount."); }
        if (amount.signum() <= 0 || amount.scale() > 2)
            throw new IllegalArgumentException("Amount must be positive with at most two decimal places.");
        LocalDate date;
        try { date = LocalDate.parse(f.value("date").trim()); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("Enter a valid date."); }
        String notes = f.value("notes").trim();
        if (notes.length() > 255) throw new IllegalArgumentException("Notes must be 255 characters or fewer.");
        p.setString(1, title); p.setString(2, category); p.setBigDecimal(3, amount);
        p.setDate(4, Date.valueOf(date));
        if (notes.isEmpty()) p.setNull(5, Types.VARCHAR); else p.setString(5, notes);
    }

    private static Form readForm(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(16_385);
        if (bytes.length > 16_384) throw new IllegalArgumentException("Form is too large.");
        Map<String, String> values = new HashMap<>();
        for (String pair : new String(bytes, StandardCharsets.UTF_8).split("&")) {
            if (pair.isEmpty()) continue;
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return new Form(values);
    }

    private static void handleStatic(HttpExchange e) throws IOException {
        String path = e.getRequestURI().getPath();
        if (!"GET".equals(e.getRequestMethod())) {
            sendError(e, 405, "Method not allowed."); e.close(); return;
        }
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..") || path.contains("\\")) {
            sendError(e, 404, "File not found."); e.close(); return;
        }
        try (var in = ExpenseTrackerApp.class.getResourceAsStream("/static" + path)) {
            if (in == null) sendError(e, 404, "File not found.");
            else {
                String type = path.endsWith(".css") ? "text/css; charset=utf-8"
                        : path.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/html; charset=utf-8";
                byte[] bytes = in.readAllBytes();
                e.getResponseHeaders().set("Content-Type", type);
                e.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                e.sendResponseHeaders(200, bytes.length);
                e.getResponseBody().write(bytes);
            }
        } finally { e.close(); }
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
    private static String jsonNullable(String value) { return value == null ? "null" : jsonString(value); }
    private static void sendError(HttpExchange e, int status, String message) throws IOException {
        send(e, status, JSON, "{\"error\":" + jsonString(message == null ? "Invalid request." : message) + "}");
    }
    private static void send(HttpExchange e, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", type);
        e.getResponseHeaders().set("Cache-Control", "no-store");
        e.sendResponseHeaders(status, bytes.length);
        e.getResponseBody().write(bytes);
    }
    private record Form(Map<String, String> values) {
        String value(String key) { return values.getOrDefault(key, ""); }
    }
}