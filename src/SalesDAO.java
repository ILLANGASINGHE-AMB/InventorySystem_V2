import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SalesDAO {

    public int getMaxBillNoOr(int fallback) throws Exception {
        String sql = "SELECT COALESCE(MAX(bill_no), ?) FROM sales";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, fallback);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : fallback;
        }
    }

    public void insert(SaleRecord s) throws Exception {
        String sql = """
            INSERT INTO sales(bill_no, customer_name, phone, type, qty, unit_price, total, date_time, payment_method)
            VALUES(?,?,?,?,?,?,?,?,?)
        """;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, s.billNo);
            ps.setString(2, s.customerName);
            ps.setString(3, s.phone);
            ps.setString(4, s.type);
            ps.setInt(5, s.qty);
            ps.setDouble(6, s.unitPrice);
            ps.setDouble(7, s.total);
            ps.setString(8, s.dateTime);
            ps.setString(9, s.payment);
            ps.executeUpdate();
        }
    }

    public List<SaleRecord> getAll() throws Exception {
        List<SaleRecord> list = new ArrayList<>();
        String sql = "SELECT id,bill_no,customer_name,phone,type,qty,unit_price,total,date_time,payment_method FROM sales ORDER BY date_time DESC";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new SaleRecord(
                        rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getInt(6), rs.getDouble(7), rs.getDouble(8),
                        rs.getString(9), rs.getString(10)
                ));
            }
        }
        return list;
    }

    public int countByDate(String yyyyMMdd) throws Exception {
        String sql = "SELECT COUNT(*) FROM sales WHERE substr(date_time,1,10)=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, yyyyMMdd);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
    public void deleteAll() throws Exception {
        try (var c = DatabaseConnection.getConnection();
             var s = c.createStatement()) {
            s.executeUpdate("DELETE FROM sales");
        }
    }

    public double sumByDate(String yyyyMMdd) throws Exception {
        String sql = "SELECT COALESCE(SUM(total),0) FROM sales WHERE substr(date_time,1,10)=?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, yyyyMMdd);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }
}
