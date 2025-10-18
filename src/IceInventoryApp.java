import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.function.IntSupplier;

public class IceInventoryApp {

    private final String currentUser;

    public IceInventoryApp(String currentUser) {
        this.currentUser = (currentUser == null || currentUser.isEmpty()) ? "Admin" : currentUser;
    }

    private JFrame frame;
    private JTable inventoryTable;
    private DefaultTableModel inventoryModel;

    private JTable salesTable;
    private DefaultTableModel salesModel;

    private JTextArea billPreview;
    private JLabel statusLabel;
    private int billCounter = 1000;


    private JPanel cardsPanel;
    private JPanel inventoryViewPanel;
    private JPanel salesHistoryPanel;
    private JPanel reportsPanel;
    private JPanel settingsPanel;

    // DAOs
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final SalesDAO salesDAO = new SalesDAO();

    public static void main(String[] args) {
        try {
            DatabaseConnection.ensureSchema();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "DB init failed: " + e.getMessage());
            return;
        }
        SwingUtilities.invokeLater(() -> {
            LoginDialog login = new LoginDialog(null);
            login.setVisible(true);
            if (!login.isSucceeded()) System.exit(0);
            IceInventoryApp app = new IceInventoryApp(login.getUsername());
            app.initialize();
        });
    }

    private void initialize() {
        try {
            inventoryDAO.seedIfEmpty();
            billCounter = Math.max(billCounter, salesDAO.getMaxBillNoOr(1000));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Seeding failed: " + e.getMessage());
        }

        frame = new JFrame("Sagacious PVT Holdings - Inventory Management");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 760);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        frame.add(buildLeftNav(), BorderLayout.WEST);
        frame.add(buildHeader(), BorderLayout.NORTH);

        cardsPanel = new JPanel(new CardLayout());
        inventoryViewPanel = buildInventoryPanel();
        salesHistoryPanel = buildSalesHistoryPanel();
        reportsPanel = buildReportsPanel();
        settingsPanel = buildSettingsPanel();

        cardsPanel.add(inventoryViewPanel, "inventory");
        cardsPanel.add(salesHistoryPanel, "salesHistory");
        cardsPanel.add(reportsPanel, "reports");
        cardsPanel.add(settingsPanel, "settings");

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setLeftComponent(cardsPanel);
        mainSplit.setRightComponent(buildSalesPanel());
        mainSplit.setDividerLocation(760);
        frame.add(mainSplit, BorderLayout.CENTER);

        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(new EmptyBorder(6, 10, 6, 10));
        frame.add(statusLabel, BorderLayout.SOUTH);

        showCard("inventory");
        frame.setVisible(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 12, 8, 12));
        header.setBackground(new Color(230, 245, 255));

        JLabel appTitle = new JLabel("Sagacious Ice Factory");
        appTitle.setFont(appTitle.getFont().deriveFont(Font.BOLD, 16f));
        header.add(appTitle, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JTextField search = new JTextField(20);
        search.setToolTipText("Search inventory by Item ID...");
        right.add(search);

        JLabel dt = new JLabel();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dt.setText(df.format(new java.util.Date()));
        new javax.swing.Timer(1000, e -> dt.setText(df.format(new java.util.Date()))).start();
        right.add(dt);

        right.add(new JLabel(currentUser));

        header.add(right, BorderLayout.EAST);

        // search filter by item id
        search.addCaretListener(e -> {
            String term = search.getText().trim().toLowerCase();
            refreshInventoryTable(term);
        });

        return header;
    }

    private JPanel buildLeftNav() {
        JPanel nav = new JPanel();
        nav.setPreferredSize(new Dimension(180, 0));
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(new EmptyBorder(12, 8, 12, 8));
        nav.setBackground(new Color(230, 245, 255));

        JLabel title = new JLabel("<html><b style='color:black'>Sagacious</b><br/><small style='color:#0B6FA8'>IMS</small></html>");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(title);
        nav.add(Box.createRigidArea(new Dimension(0, 12)));

        String[][] items = {
                {"Inventory", "inventory"},
                {"sales (customer details)", "salesHistory"},
                {"reports (daily)", "reports"},
                {"settings (theme)", "settings"}
        };

        for (String[] it : items) {
            JButton btn = new JButton(it[0]);
            String card = it[1];
            btn.setAlignmentX(Component.LEFT_ALIGNMENT);
            btn.setMaximumSize(new Dimension(160, 40));
            btn.setFocusable(false);
            btn.addActionListener(e -> showCard(card));
            nav.add(btn);
            nav.add(Box.createRigidArea(new Dimension(0, 8)));
        }
        return nav;
    }

    private JPanel buildInventoryPanel() {
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(new EmptyBorder(12, 12, 12, 12));
        center.setBackground(Color.WHITE);

        // Top stat cards (using safe(...) wrapper)
        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
        cards.setPreferredSize(new Dimension(0, 100));
        cards.add(makeStatCard("Total Cubes (Manufactured)",
                () -> safe(() -> inventoryDAO.totalByType("Manufactured"))));
        cards.add(makeStatCard("Total Cubes (Resell)",
                () -> safe(() -> inventoryDAO.totalByType("Resell"))));
        cards.add(makeStatCard("Useless Cubes (Waste)",
                () -> safe(inventoryDAO::totalWaste)));
        cards.add(makeStatCard("Available Stock",
                () -> safe(inventoryDAO::totalStock)));
        center.add(cards, BorderLayout.NORTH);


        String[] cols = {"Item ID", "Type", "Quantity", "Unit Price (LKR)", "Last Updated"};
        inventoryModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 3; }
            @Override public void setValueAt(Object aValue, int row, int column) {
                super.setValueAt(aValue, row, column);
                if (column == 3) {
                    String id = (String) getValueAt(row, 0);
                    try {
                        String raw = String.valueOf(aValue).replaceAll("LKR","").replaceAll(",","").trim();
                        double v = Double.parseDouble(raw);
                        inventoryDAO.updatePrice(id, v);
                        statusLabel.setText("Unit price updated for " + id);
                        refreshInventoryTable(null);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(frame, "Price update failed: " + ex.getMessage());
                        refreshInventoryTable(null);
                    }
                }
            }
        };
        inventoryTable = new JTable(inventoryModel);
        inventoryTable.setRowHeight(28);

        // LKR renderer
        inventoryTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public void setValue(Object value) {
                try {
                    double val = (value instanceof Number) ? ((Number) value).doubleValue()
                            : Double.parseDouble(String.valueOf(value).replaceAll("LKR","").trim());
                    setText(String.format("LKR %.2f", val));
                } catch (Exception e) { setText(String.valueOf(value)); }
            }
        });


        inventoryTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String type = String.valueOf(table.getModel().getValueAt(row, 1));
                int qty = Integer.parseInt(String.valueOf(table.getModel().getValueAt(row, 2)));
                if ("WASTE".equalsIgnoreCase(type)) c.setForeground(Color.RED.darker());
                else if (qty <= 5) c.setForeground(new Color(180,85,0));
                else c.setForeground(Color.BLACK);
                return c;
            }
        });

        center.add(new JScrollPane(inventoryTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton addBtn = new JButton("Add Cubes");
        addBtn.addActionListener(e -> openAddItemDialog());
        JButton removeBtn = new JButton("Remove Cubes");
        removeBtn.addActionListener(e -> openRemoveDialog());
        JButton exportBtn = new JButton("Export Inventory CSV");
        exportBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("CSV", "csv"));
            int rc = chooser.showSaveDialog(frame);
            if (rc == JFileChooser.APPROVE_OPTION) {
                try {
                    String path = chooser.getSelectedFile().getAbsolutePath();
                    if (!path.endsWith(".csv")) path += ".csv";
                    inventoryDAO.exportCsv(path);
                    JOptionPane.showMessageDialog(frame, "Exported inventory CSV.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Export failed: " + ex.getMessage());
                }
            }
        });
        actions.add(addBtn); actions.add(removeBtn); actions.add(exportBtn);
        center.add(actions, BorderLayout.SOUTH);

        // initial load
        refreshInventoryTable(null);
        return center;
    }

    private JPanel makeStatCard(String title, IntSupplier supplier) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        card.setPreferredSize(new Dimension(0, 80));
        card.setBackground(Color.WHITE);

        JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 14f));
        card.add(lblTitle, BorderLayout.NORTH);

        JLabel lblValue = new JLabel(String.valueOf(supplier.getAsInt()), SwingConstants.CENTER);
        lblValue.setFont(lblValue.getFont().deriveFont(Font.PLAIN, 18f));
        card.add(lblValue, BorderLayout.CENTER);

        new javax.swing.Timer(2000, e -> lblValue.setText(String.valueOf(supplier.getAsInt()))).start();
        return card;
    }

    private JPanel buildSalesHistoryPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        p.setBackground(Color.WHITE);

        String[] cols = {"Bill No", "Customer", "Phone", "Type", "Qty", "Unit Price (LKR)", "Total (LKR)", "Date & Time", "Payment"};
        salesModel = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        salesTable = new JTable(salesModel);
        salesTable.setRowHeight(26);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshSalesTable());

        p.add(refresh, BorderLayout.NORTH);
        p.add(new JScrollPane(salesTable), BorderLayout.CENTER);

        refreshSalesTable();
        return p;
    }

    private JPanel buildReportsPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        p.setBackground(Color.WHITE);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.setOpaque(false);
        controls.add(new JLabel("Select date (yyyy-MM-dd):"));
        JTextField dateField = new JTextField(new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()), 10);
        controls.add(dateField);
        JButton run = new JButton("Show Report");
        controls.add(run);
        p.add(controls);

        JTextArea reportArea = new JTextArea(10, 40);
        reportArea.setEditable(false);
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane rsp = new JScrollPane(reportArea);
        p.add(rsp);

        run.addActionListener(e -> {
            String dateStr = dateField.getText().trim();
            try {
                int count = salesDAO.countByDate(dateStr);
                double total = salesDAO.sumByDate(dateStr);
                StringBuilder sb = new StringBuilder();
                sb.append("Daily Sales Report for ").append(dateStr).append("\n");
                sb.append("--------------------------------\n");
                sb.append(String.format("Transactions: %d\n", count));
                sb.append(String.format("Total Sales (LKR): %.2f\n", total));
                reportArea.setText(sb.toString());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Report failed: " + ex.getMessage());
            }
        });

        run.doClick();
        return p;
    }

    private JPanel buildSettingsPanel() {


        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        p.setBackground(Color.WHITE);

        JLabel info = new JLabel("Settings (Theme toggle example)");
        p.add(info);
        p.add(Box.createVerticalStrut(10));

        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> {
            frame.dispose();
            LoginDialog login = new LoginDialog(null);
            login.setVisible(true);
            if (login.isSucceeded()) {
                new IceInventoryApp(login.getUsername()).initialize();
            } else {
                System.exit(0);
            }
        });
        p.add(logout);


        JButton reset = new JButton("Reset (Delete ALL data)");
        reset.addActionListener(e -> {
            int rc = JOptionPane.showConfirmDialog(frame,
                    "This will permanently delete ALL inventory and sales data.\nContinue?",
                    "Confirm Reset", JOptionPane.YES_NO_OPTION);
            if (rc != JOptionPane.YES_OPTION) return;

            try {
                salesDAO.deleteAll();
                inventoryDAO.deleteAll();
                DatabaseConnection.getConnection().createStatement().execute("VACUUM"); // optional
                billCounter = Math.max(1000, salesDAO.getMaxBillNoOr(1000));
                refreshInventoryTable(null);
                refreshSalesTable();
                JOptionPane.showMessageDialog(frame, "All data deleted.");
                statusLabel.setText("All data deleted.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Reset failed: " + ex.getMessage());
            }
        });
        p.add(Box.createVerticalStrut(8));
        p.add(reset);

        return p;
    }

    private JPanel buildSalesPanel() {
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(new EmptyBorder(12, 12, 12, 12));
        right.setBackground(Color.WHITE);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createTitledBorder("Sell Ice Cubes"));
        form.setOpaque(false);

        JTextField customerName = new JTextField();
        form.add(makeField("Customer Name:", customerName));

        JTextField customerPhone = new JTextField();
        form.add(makeField("Customer Phone No:", customerPhone));

        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Manufactured", "Resell"});
        form.add(makeField("Type of Cube:", typeCombo));

        JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
        form.add(makeField("Number of Cubes:", qtySpinner));

        JFormattedTextField unitPrice = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        unitPrice.setColumns(10); unitPrice.setValue(0.0);
        form.add(makeField("Unit Price (LKR):", unitPrice));

        JPanel paymentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton cash = new JRadioButton("Cash", true);
        JRadioButton credit = new JRadioButton("Credit");
        ButtonGroup bg = new ButtonGroup(); bg.add(cash); bg.add(credit);
        paymentPanel.add(cash); paymentPanel.add(credit);
        form.add(makeField("Payment Method:", paymentPanel));

        JTextField dateTime = new JTextField(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        form.add(makeField("Date & Time:", dateTime));
        JButton nowBtn = new JButton("Now");
        nowBtn.addActionListener(e -> dateTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())));
        form.add(nowBtn);

        JButton gen = new JButton("Generate Bill");
        gen.addActionListener(e -> {
            String name = customerName.getText().trim();
            String phone = customerPhone.getText().trim();
            String type = String.valueOf(typeCombo.getSelectedItem());
            int qty = (Integer) qtySpinner.getValue();
            double unit = ((Number) unitPrice.getValue()).doubleValue();
            String pay = cash.isSelected() ? "Cash" : "Credit";
            String dt = dateTime.getText().trim();

            if (name.isEmpty() || phone.isEmpty()) { JOptionPane.showMessageDialog(frame, "Enter customer name & phone."); return; }
            if (qty <= 0 || unit <= 0) { JOptionPane.showMessageDialog(frame, "Qty and Unit Price must be > 0."); return; }

            try {
                if (!inventoryDAO.deductByType(type, qty)) {
                    JOptionPane.showMessageDialog(frame, "Insufficient stock for type: " + type);
                    return;
                }
                int newBill = ++billCounter;
                double total = qty * unit;
                salesDAO.insert(new SaleRecord(0, newBill, name, phone, type, qty, unit, total, dt, pay));
                billPreview.setText(generateBillText(newBill, name, phone, type, qty, unit, pay, dt));
                refreshInventoryTable(null);
                refreshSalesTable();
                JOptionPane.showMessageDialog(frame, "Sale recorded — Bill #" + newBill + " generated");
                statusLabel.setText("Sale recorded — Bill #" + newBill);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Sale failed: " + ex.getMessage());
            }
        });

        form.add(Box.createVerticalStrut(8));
        form.add(gen);

        JPanel billPanel = new JPanel(new BorderLayout());
        billPanel.setBorder(BorderFactory.createTitledBorder("Bill Preview"));
        billPanel.setOpaque(false);

        billPreview = new JTextArea(12, 30);
        billPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        billPreview.setEditable(false);
        billPanel.add(new JScrollPane(billPreview), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton printBtn = new JButton("Print");
        printBtn.addActionListener(e -> { try { billPreview.print(); } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Print failed: " + ex.getMessage()); } });
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Text File", "txt"));
            int rc = chooser.showSaveDialog(frame);
            if (rc == JFileChooser.APPROVE_OPTION) {
                try {
                    String path = chooser.getSelectedFile().getAbsolutePath();
                    if (!path.endsWith(".txt")) path += ".txt";
                    java.nio.file.Files.write(java.nio.file.Paths.get(path), billPreview.getText().getBytes());
                    JOptionPane.showMessageDialog(frame, "Saved to " + path);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Save failed: " + ex.getMessage());
                }
            }
        });
        actions.add(printBtn); actions.add(saveBtn);
        billPanel.add(actions, BorderLayout.SOUTH);

        right.add(form, BorderLayout.NORTH);
        right.add(billPanel, BorderLayout.CENTER);
        return right;
    }

    private JPanel makeField(String label, Component field) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(140, 24));
        p.add(l, BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        p.setBorder(new EmptyBorder(6,0,6,0));
        return p;
    }

    private void refreshInventoryTable(String filterLower) {
        try {
            List<InventoryItem> items = inventoryDAO.getAll();
            inventoryModel.setRowCount(0);
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (InventoryItem it : items) {
                if (filterLower != null && !filterLower.isEmpty() && !it.itemId.toLowerCase().contains(filterLower))
                    continue;
                inventoryModel.addRow(new Object[]{
                        it.itemId, it.type, it.quantity, it.unitPrice, df.format(new java.util.Date(it.lastUpdated))
                });
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Load inventory failed: " + e.getMessage());
        }
    }

    private void openAddItemDialog() {
        JDialog dlg = new JDialog(frame, "Add Inventory Item", true);
        dlg.setSize(420, 320);
        dlg.setLocationRelativeTo(frame);
        dlg.setLayout(new BorderLayout());

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JTextField idField = new JTextField("AUTO-" + System.currentTimeMillis()%100000);
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Manufactured", "Resell", "WASTE"});
        JSpinner qty = new JSpinner(new SpinnerNumberModel(1, 0, 100000, 1));
        JFormattedTextField price = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        price.setValue(0.0);

        p.add(makeField("Item ID:", idField));
        p.add(makeField("Type:", typeCombo));
        p.add(makeField("Quantity:", qty));
        p.add(makeField("Unit Price (LKR):", price));

        JButton add = new JButton("Add Item");
        add.addActionListener(e -> {
            try {
                String id = idField.getText().trim();
                if (id.isEmpty()) { JOptionPane.showMessageDialog(dlg, "Item ID required."); return; }
                String type = (String) typeCombo.getSelectedItem();
                int q = (Integer) qty.getValue();
                double pval = ((Number) price.getValue()).doubleValue();
                inventoryDAO.addItem(id, type, q, pval);
                refreshInventoryTable(null);
                dlg.dispose();
                statusLabel.setText("Added inventory item: " + id);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Add failed: " + ex.getMessage());
            }
        });

        dlg.add(p, BorderLayout.CENTER);
        dlg.add(add, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void openRemoveDialog() {
        String id = JOptionPane.showInputDialog(frame, "Enter Item ID to remove from (e.g., I-100):");
        if (id == null || id.trim().isEmpty()) return;
        String qStr = JOptionPane.showInputDialog(frame, "Enter quantity to remove:");
        if (qStr == null) return;
        try {
            int q = Integer.parseInt(qStr.trim());
            if (q <= 0) throw new NumberFormatException();
            inventoryDAO.removeQuantity(id.trim(), q);
            refreshInventoryTable(null);
            statusLabel.setText("Removed " + q + " from " + id);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Invalid number.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Remove failed: " + ex.getMessage());
        }
    }

    private void refreshSalesTable() {
        try {
            List<SaleRecord> sales = salesDAO.getAll();
            salesModel.setRowCount(0);
            for (SaleRecord s : sales) {
                salesModel.addRow(new Object[]{
                        s.billNo, s.customerName, s.phone, s.type, s.qty,
                        String.format("%.2f", s.unitPrice), String.format("%.2f", s.total),
                        s.dateTime, s.payment
                });
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Load sales failed: " + e.getMessage());
        }
    }

    private String generateBillText(int billNo, String customerName, String phone, String type, int qty, double unitPrice, String paymentMethod, String dateTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("            Sagacious PVT Holdings - Bill\n");
        sb.append("            -------------------\n");
        sb.append(String.format("Bill No: %d\n", billNo));
        sb.append(String.format("Date & Time: %s\n", dateTime));
        sb.append("\n");
        sb.append("Customer:\n");
        sb.append(String.format("  %s\n", customerName));
        sb.append(String.format("  Phone: %s\n", phone));
        sb.append("\n");
        sb.append("Items:\n");
        double subtotal = qty * unitPrice;
        sb.append(String.format("  %-15s %5d x LKR %8.2f = LKR %8.2f\n", type, qty, unitPrice, subtotal));
        double tax = 0.0;
        double total = subtotal + tax;
        sb.append("\n");
        sb.append(String.format("Subtotal: LKR %.2f\n", subtotal));
        sb.append(String.format("Tax: LKR %.2f\n", tax));
        sb.append(String.format("Total: LKR %.2f\n", total));
        sb.append("\n");
        sb.append("Payment Method: ").append(paymentMethod).append("\n\n");
        sb.append("Thank you for your purchase!\n");
        return sb.toString();
    }

    private void showCard(String cardName) {
        CardLayout cl = (CardLayout) (cardsPanel.getLayout());
        cl.show(cardsPanel, cardName);
        if ("salesHistory".equals(cardName)) refreshSalesTable();
        if ("inventory".equals(cardName)) refreshInventoryTable(null);
    }


    @FunctionalInterface
    private interface ThrowingIntSupplier { int getAsInt() throws Exception; }

    private int safe(ThrowingIntSupplier supplier) {
        try { return supplier.getAsInt(); }
        catch (Exception e) { return 0; }
    }
}
