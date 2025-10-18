import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class InventoryDAO {

    public void seedIfEmpty() throws Exception {
        if (getAll().isEmpty()) {
            upsert(new InventoryItem("I-100", "Manufactured", 500, 0.50, System.currentTimeMillis()));
            upsert(new InventoryItem("I-101", "Manufactured", 50, 1.10, System.currentTimeMillis()));
            upsert(new InventoryItem("R-200", "Resell", 200, 0.80, System.currentTimeMillis()));
            upsert(new InventoryItem("R-201", "Resell", 30, 0.45, System.currentTimeMillis()));
            upsert(new InventoryItem("W-001", "WASTE", 12, 0.00, System.currentTimeMillis()));
        }
    }

    public List<InventoryItem> getAll() throws Exception {
        List<InventoryItem> list = new ArrayList<>();
        String sql = "SELECT item_id,type,quantity,unit_price,last_updated FROM inventory ORDER BY item_id";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new InventoryItem(
                        rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getDouble(4), rs.getLong(5)
                ));
            }
        }
        return list;
    }

    public void upsert(InventoryItem it) throws Exception {
        String sql = """
            INSERT INTO inventory(item_id,type,quantity,unit_price,last_updated)
            VALUES(?,?,?,?,?)
            ON CONFLICT(item_id) DO UPDATE SET
              type=excluded.type,
              quantity=excluded.quantity,
              unit_price=excluded.unit_price,
              last_updated=excluded.last_updated
        """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, it.itemId);
            ps.setString(2, it.type);
            ps.setInt(3, it.quantity);
            ps.setDouble(4, it.unitPrice);
            ps.setLong(5, it.lastUpdated);
            ps.executeUpdate();
        }
    }

    public void addItem(String itemId, String type, int qty, double price) throws Exception {
        upsert(new InventoryItem(itemId, type, qty, price, System.currentTimeMillis()));
    }

    public void updatePrice(String itemId, double price) throws Exception {
        String sql = "UPDATE inventory SET unit_price=?, last_updated=? WHERE item_id=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, price);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, itemId);
            ps.executeUpdate();
        }
    }

    public void removeQuantity(String itemId, int qty) throws Exception {
        String sql = "UPDATE inventory SET quantity = quantity - ?, last_updated=? WHERE item_id=? AND quantity >= ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, itemId);
            ps.setInt(4, qty);
            int updated = ps.executeUpdate();
            if (updated == 0) throw new IllegalStateException("Insufficient quantity for item " + itemId);
        }
        deleteIfZero(itemId);
    }

    private void deleteIfZero(String itemId) throws Exception {
        String sql = "DELETE FROM inventory WHERE item_id=? AND quantity <= 0";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.executeUpdate();
        }
    }

    /** Deducts from items of given type, choosing the largest stocks first (like your Swing logic). */
    public boolean deductByType(String type, int qtyNeeded) throws Exception {
        String sql = "SELECT item_id,quantity FROM inventory WHERE type=? ORDER BY quantity DESC";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type);
            ResultSet rs = ps.executeQuery();

            List<String> ids = new ArrayList<>();
            List<Integer> qtys = new ArrayList<>();
            int total = 0;
            while (rs.next()) {
                ids.add(rs.getString(1));
                int q = rs.getInt(2);
                qtys.add(q);
                total += q;
            }
            if (total < qtyNeeded) return false;

            int remain = qtyNeeded;
            for (int i = 0; i < ids.size() && remain > 0; i++) {
                String id = ids.get(i);
                int have = qtys.get(i);
                int take = Math.min(have, remain);
                removeQuantity(id, take);
                remain -= take;
            }
            return true;
        }
    }

    public int totalByType(String type) throws Exception {
        String sql = "SELECT COALESCE(SUM(quantity),0) FROM inventory WHERE type=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int totalWaste() throws Exception {
        return totalByType("WASTE");
    }

    public int totalStock() throws Exception {
        String sql = "SELECT COALESCE(SUM(quantity),0) FROM inventory";
        try (Connection c = DatabaseConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
    public void deleteAll() throws Exception {
        try (var c = DatabaseConnection.getConnection();
             var s = c.createStatement()) {
            s.executeUpdate("DELETE FROM inventory");
        }
    }

    public void exportCsv(String path) throws Exception {
        List<InventoryItem> items = getAll();
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("ItemID,Type,Quantity,UnitPrice(LKR),LastUpdated");
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (InventoryItem it : items) {
                pw.printf("%s,%s,%d,%.2f,%s%n",
                        it.itemId, it.type, it.quantity, it.unitPrice, df.format(new java.util.Date(it.lastUpdated)));
            }
        }
    }
}
