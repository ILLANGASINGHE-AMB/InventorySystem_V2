import java.sql.*;

public class DatabaseConnection {
    private static final String DB_URL = "jdbc:sqlite:inventory.db";
    private static volatile Connection INSTANCE;

    public static Connection getConnection() throws SQLException {
        if (INSTANCE == null || INSTANCE.isClosed()) {
            INSTANCE = DriverManager.getConnection(DB_URL);
            INSTANCE.createStatement().execute("PRAGMA foreign_keys = ON");
        }
        return INSTANCE;
    }

    /** Creates tables if they do not exist. Call once on startup. */
    public static void ensureSchema() throws SQLException {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS inventory (
                  item_id TEXT PRIMARY KEY,
                  type TEXT NOT NULL,            -- Manufactured | Resell | WASTE
                  quantity INTEGER NOT NULL,
                  unit_price REAL NOT NULL,
                  last_updated INTEGER NOT NULL  -- epoch millis
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS sales (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  bill_no INTEGER NOT NULL,
                  customer_name TEXT NOT NULL,
                  phone TEXT NOT NULL,
                  type TEXT NOT NULL,            -- Manufactured | Resell
                  qty INTEGER NOT NULL,
                  unit_price REAL NOT NULL,
                  total REAL NOT NULL,
                  date_time TEXT NOT NULL,       -- "yyyy-MM-dd HH:mm:ss"
                  payment_method TEXT NOT NULL   -- Cash | Credit
                )
            """);
            // Fast LIKE searches, etc (optional)
            s.execute("CREATE INDEX IF NOT EXISTS idx_inventory_type ON inventory(type)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_sales_date ON sales(date_time)");
        }
    }
}
