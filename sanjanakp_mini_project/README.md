# Pocket Expense Tracker

A small expense tracker built with Java 17, HTML, CSS, JavaScript, JDBC, and MySQL. Java serves the frontend files and handles CRUD requests. Expense records are stored in MySQL.

## Requirements

- JDK 17+
- Maven 3.8+
- MySQL 8+

## Setup

1. Run database.sql in MySQL Workbench or the MySQL command line. It creates the expense_tracker_db database and expenses table.
2. Set DB_URL (default jdbc:mysql://localhost:3306/expense_tracker_db?serverTimezone=UTC), DB_USER (default root), and DB_PASSWORD (default blank) if your MySQL credentials differ.
3. From this project folder, run mvn clean compile exec:java.
4. Open http://127.0.0.1:8080 in a browser.

PowerShell example:

$env:DB_USER = 'root'
$env:DB_PASSWORD = 'your-password'
mvn clean compile exec:java

## Features

- Add, view, update, and delete expenses in MySQL
- Search expense names, categories, and notes
- Spending summary with total, entry count, and top category
- Client-side and server-side validation
- Responsive layout for desktop and mobile

## Project files

- src/main/java/com/example/expensetracker/ — Java HTTP server and MySQL connection
- src/main/resources/static/index.html — HTML structure
- src/main/resources/static/styles.css — page styling
- src/main/resources/static/app.js — browser interactions
- database.sql — database and table setup