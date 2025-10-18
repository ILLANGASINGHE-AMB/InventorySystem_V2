import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class LoginDialog extends JDialog {
    private JTextField userField;
    private JPasswordField passField;
    private boolean succeeded = false;
    private String username = "";

    // Demo users (username -> password). (Per your choice: no DB user mgmt for now)
    private static final Map<String,String> USERS = new HashMap<>();
    static {
        USERS.put("admin", "admin123");
        USERS.put("staff", "staff123");
    }

    public LoginDialog(Frame parent) {
        super(parent, "Sign in", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(360, 200);
        setLocationRelativeTo(parent);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new javax.swing.border.EmptyBorder(12,12,12,12));

        userField = new JTextField();
        passField = new JPasswordField();

        panel.add(makeRow("Username:", userField));
        panel.add(makeRow("Password:", passField));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnLogin = new JButton("Login");
        JButton btnCancel = new JButton("Cancel");
        btns.add(btnLogin); btns.add(btnCancel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(btns);

        btnLogin.addActionListener(e -> {
            String u = userField.getText().trim();
            String p = new String(passField.getPassword());
            if (authenticate(u, p)) {
                username = u;
                succeeded = true;
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Invalid username or password.");
                passField.setText("");
                succeeded = false;
            }
        });
        btnCancel.addActionListener(e -> {
            succeeded = false;
            dispose();
        });

        setContentPane(panel);
    }

    private JPanel makeRow(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(8,0));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(100, 24));
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private boolean authenticate(String u, String p) {
        return USERS.containsKey(u) && USERS.get(u).equals(p);
    }
    public boolean isSucceeded() { return succeeded; }
    public String getUsername() { return username; }
}
