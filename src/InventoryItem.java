public class InventoryItem {
    public String itemId;
    public String type;
    public int quantity;
    public double unitPrice;
    public long lastUpdated;

    public InventoryItem(String itemId, String type, int quantity, double unitPrice, long lastUpdated) {
        this.itemId = itemId;
        this.type = type;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lastUpdated = lastUpdated;
    }
}
