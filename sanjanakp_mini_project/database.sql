CREATE DATABASE IF NOT EXISTS expense_tracker_db;
USE expense_tracker_db;

CREATE TABLE IF NOT EXISTS expenses (
    id INT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    expense_date DATE NOT NULL,
    notes VARCHAR(255),
    CONSTRAINT chk_expense_amount CHECK (amount > 0)
);

-- Optional example row; the application can also add records through its form.
INSERT INTO expenses (title, category, amount, expense_date, notes)
VALUES ('Lunch', 'Food', 12.50, CURRENT_DATE, 'Example expense');
