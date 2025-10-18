public class SaleRecord {
    public long id;           // autoincrement
    public int billNo;
    public String customerName;
    public String phone;
    public String type;       // Manufactured | Resell
    public int qty;
    public double unitPrice;
    public double total;
    public String dateTime;   // yyyy-MM-dd HH:mm:ss
    public String payment;

    public SaleRecord(long id, int billNo, String customerName, String phone, String type, int qty, double unitPrice, double total, String dateTime, String payment) {
        this.id = id;
        this.billNo = billNo;
        this.customerName = customerName;
        this.phone = phone;
        this.type = type;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.total = total;
        this.dateTime = dateTime;
        this.payment = payment;
    }
}
