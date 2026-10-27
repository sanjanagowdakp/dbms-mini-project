package com.example.expensetracker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

final class Database {
    private Database() {}

    static Connection connect() throws SQLException {
        String url = setting("DB_URL", "jdbc:mysql://localhost:3306/expense_tracker_db?serverTimezone=UTC");
        String user = setting("DB_USER", "root");
        String password = setting("DB_PASSWORD", "");
        return DriverManager.getConnection(url, user, password);
    }

    private static String setting(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
