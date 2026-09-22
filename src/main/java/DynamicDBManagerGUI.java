import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class DynamicDBManagerGUI extends JFrame {
    private DynamicDAO dao;
    private JTabbedPane tabbedPane;
    private JList<String> tableList;
    private DefaultListModel<String> listModel;

    public void start() {
        File dbFolder = new File("database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }

        while (true) {
            File[] files = dbFolder.listFiles((dir, name) -> name.endsWith(".db"));
            List<String> dbFiles = new ArrayList<>();
            if (files != null) {
                for (File f : files) dbFiles.add(f.getName());
            }

            dbFiles.add("++ Create New Database ++");
            
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            JComboBox<String> fileCombo = new JComboBox<>(dbFiles.toArray(new String[0]));
            panel.add(new JLabel("Select a Database:"), BorderLayout.NORTH);
            panel.add(fileCombo, BorderLayout.CENTER);

            Object[] options = {"Open", "Delete", "Exit"};
            int result = JOptionPane.showOptionDialog(null, panel, "Database Selector",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);

            if (result == 2 || result == JOptionPane.CLOSED_OPTION) { // Exit
                System.exit(0);
            } else if (result == 1) { // Delete
                String selected = (String) fileCombo.getSelectedItem();
                if (selected != null && !selected.startsWith("++")) {
                    try {
                        Files.delete(new File("database/" + selected).toPath());
                        JOptionPane.showMessageDialog(null, "Deleted successfully.");
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(null, "Error deleting file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else if (result == 0) { // Open
                String selected = (String) fileCombo.getSelectedItem();
                if (selected != null) {
                    if (selected.startsWith("++")) {
                        String newDbName = JOptionPane.showInputDialog(null, "Enter new database name (e.g., inventory.db):");
                        if (newDbName != null && !newDbName.trim().isEmpty()) {
                            if (!newDbName.endsWith(".db")) newDbName += ".db";
                            dao = new DynamicDAO("database/" + newDbName);
                            break;
                        }
                    } else {
                        dao = new DynamicDAO("database/" + selected);
                        break;
                    }
                }
            }
        }
        
        initializeMainGUI();
        
        // Initial state check
        List<String> tables = dao.getTables();
        if (tables.isEmpty()) {
            int res = JOptionPane.showConfirmDialog(this, "No tables found in this database. Would you like to create one?", "No Tables", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                openCreateTableDialog();
            }
        }
    }

    private void initializeMainGUI() {
        setTitle("Dynamic Database Manager");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top Container: Actions for active tab on the left, MCP server status badge on the right
        JPanel topContainer = new JPanel(new BorderLayout());
        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton switchDbBtn = new JButton("Switch Database");
        JButton refreshBtn = new JButton("Refresh Data");
        JButton searchBtn = new JButton("Advanced Search");
        JButton clearSearchBtn = new JButton("Clear Search");
        JButton addBtn = new JButton("Add Record");
        JButton editBtn = new JButton("Edit Selected");
        JButton deleteBtn = new JButton("Delete Selected");

        switchDbBtn.addActionListener(e -> {
            dispose();
            new DynamicDBManagerGUI().start();
        });
        refreshBtn.addActionListener(e -> {
            TableTabPanel tab = getActiveTab();
            if (tab != null) tab.refreshTable();
        });
        searchBtn.addActionListener(e -> openSearchPanel());
        clearSearchBtn.addActionListener(e -> clearSearch());
        addBtn.addActionListener(e -> openRecordPanel(false));
        editBtn.addActionListener(e -> openRecordPanel(true));
        deleteBtn.addActionListener(e -> deleteSelectedRecord());

        leftActions.add(switchDbBtn);
        leftActions.add(new JSeparator(SwingConstants.VERTICAL));
        leftActions.add(refreshBtn);
        leftActions.add(new JSeparator(SwingConstants.VERTICAL));
        leftActions.add(addBtn);
        leftActions.add(editBtn);
        leftActions.add(deleteBtn);
        leftActions.add(new JSeparator(SwingConstants.VERTICAL));
        leftActions.add(searchBtn);
        leftActions.add(clearSearchBtn);

        // Top-right MCP server status badge
        JPanel rightStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        Icon greenDot = new StatusDotIcon(new Color(35, 175, 75), 10);
        Icon grayDot = new StatusDotIcon(new Color(150, 150, 150), 10);

        JLabel mcpStatusBadge = new JLabel("MCP Server: Offline", grayDot, SwingConstants.LEFT);
        mcpStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        mcpStatusBadge.setOpaque(true);
        mcpStatusBadge.setBackground(new Color(245, 245, 245));
        mcpStatusBadge.setForeground(new Color(110, 110, 110));
        mcpStatusBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        mcpStatusBadge.setToolTipText("Status of MCP server for AI clients (e.g. Claude Desktop)");
        rightStatus.add(mcpStatusBadge);

        topContainer.add(leftActions, BorderLayout.CENTER);
        topContainer.add(rightStatus, BorderLayout.EAST);
        add(topContainer, BorderLayout.NORTH);

        // Periodic timer to update MCP server status without blocking UI
        javax.swing.Timer mcpTimer = new javax.swing.Timer(2000, e -> {
            boolean running = isMcpRunning();
            if (running) {
                mcpStatusBadge.setIcon(greenDot);
                mcpStatusBadge.setText("MCP Server: Online");
                mcpStatusBadge.setForeground(new Color(20, 130, 50));
                mcpStatusBadge.setBackground(new Color(230, 248, 235));
                mcpStatusBadge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(110, 205, 140), 1, true),
                        BorderFactory.createEmptyBorder(4, 10, 4, 10)
                ));
                mcpStatusBadge.setToolTipText("MCP Server is running! AI models (Claude Desktop) can query this database.");
            } else {
                mcpStatusBadge.setIcon(grayDot);
                mcpStatusBadge.setText("MCP Server: Offline");
                mcpStatusBadge.setForeground(new Color(110, 110, 110));
                mcpStatusBadge.setBackground(new Color(245, 245, 245));
                mcpStatusBadge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                        BorderFactory.createEmptyBorder(4, 10, 4, 10)
                ));
                mcpStatusBadge.setToolTipText("MCP Server is offline. Start via Claude Desktop or McpServerMain.");
            }
        });
        mcpTimer.setInitialDelay(200);
        mcpTimer.start();

        // Center Panel: Tabbed Pane
        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        // Add permanent SQL Editor Tab
        SqlEditorTabPanel sqlTab = new SqlEditorTabPanel();
        tabbedPane.addTab(">_ SQL Editor", sqlTab);

        // West Panel: Table List
        JPanel westPanel = new JPanel(new BorderLayout());
        westPanel.setPreferredSize(new Dimension(200, 0));
        westPanel.setBorder(BorderFactory.createTitledBorder("Database Tables"));

        listModel = new DefaultListModel<>();
        tableList = new JList<>(listModel);
        tableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refreshTableList();

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem openCurrentItem = new JMenuItem("Open in Current Tab");
        JMenuItem openNewItem = new JMenuItem("Open in New Tab");
        JMenuItem renameItem = new JMenuItem("Rename Table");
        JMenuItem dropItem = new JMenuItem("Drop Table");

        openCurrentItem.addActionListener(e -> openTableInTab(tableList.getSelectedValue(), false));
        openNewItem.addActionListener(e -> openTableInTab(tableList.getSelectedValue(), true));
        renameItem.addActionListener(e -> renameSelectedTable());
        dropItem.addActionListener(e -> dropSelectedTable());

        popupMenu.add(openCurrentItem);
        popupMenu.add(openNewItem);
        popupMenu.addSeparator();
        popupMenu.add(renameItem);
        popupMenu.add(dropItem);

        tableList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { showPopup(e); }
            public void mouseReleased(MouseEvent e) { showPopup(e); }
            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = tableList.locationToIndex(e.getPoint());
                    if (row != -1) {
                        tableList.setSelectedIndex(row);
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openTableInTab(tableList.getSelectedValue(), true);
                }
            }
        });

        westPanel.add(new JScrollPane(tableList), BorderLayout.CENTER);

        JButton createTableBtn = new JButton("Create New Table");
        createTableBtn.addActionListener(e -> openCreateTableDialog());
        westPanel.add(createTableBtn, BorderLayout.SOUTH);

        add(westPanel, BorderLayout.WEST);

        setVisible(true);
    }

    private TableTabPanel getActiveTab() {
        Component c = tabbedPane.getSelectedComponent();
        if (c instanceof TableTabPanel) {
            return (TableTabPanel) c;
        }
        return null;
    }

    private void openTableInTab(String tableName, boolean newTab) {
        if (tableName == null) return;
        
        if (!newTab && tabbedPane.getTabCount() > 0) {
            int selected = tabbedPane.getSelectedIndex();
            if (tabbedPane.getComponentAt(selected) instanceof SqlEditorTabPanel) {
                newTab = true;
            }
        }
        
        if (!newTab && tabbedPane.getTabCount() > 0) {
            int selected = tabbedPane.getSelectedIndex();
            TableTabPanel tab = new TableTabPanel(tableName);
            tabbedPane.setComponentAt(selected, tab);
            tabbedPane.setTabComponentAt(selected, createTabHeader(tableName, tab));
        } else {
            TableTabPanel tab = new TableTabPanel(tableName);
            tabbedPane.addTab(tableName, tab);
            int index = tabbedPane.indexOfComponent(tab);
            tabbedPane.setTabComponentAt(index, createTabHeader(tableName, tab));
            tabbedPane.setSelectedComponent(tab);
        }
    }

    private JPanel createTabHeader(String title, TableTabPanel tab) {
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabHeader.setOpaque(false);
        JLabel titleLbl = new JLabel(title + " ");
        JButton closeBtn = new JButton("x");
        closeBtn.setMargin(new Insets(0, 2, 0, 2));
        closeBtn.setBorder(BorderFactory.createEmptyBorder());
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusable(false);
        closeBtn.setForeground(Color.GRAY);
        closeBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { closeBtn.setForeground(Color.RED); }
            public void mouseExited(MouseEvent e) { closeBtn.setForeground(Color.GRAY); }
        });
        closeBtn.addActionListener(e -> {
            int i = tabbedPane.indexOfComponent(tab);
            if (i != -1) tabbedPane.removeTabAt(i);
        });
        tabHeader.add(titleLbl);
        tabHeader.add(closeBtn);
        return tabHeader;
    }

    private void refreshTableList() {
        listModel.clear();
        for (String t : dao.getTables()) {
            listModel.addElement(t);
        }
    }

    private void renameSelectedTable() {
        String oldName = tableList.getSelectedValue();
        if (oldName == null) return;
        String newName = JOptionPane.showInputDialog(this, "Enter new name for table '" + oldName + "':", oldName);
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
            if (dao.renameTable(oldName, newName)) {
                JOptionPane.showMessageDialog(this, "Table renamed.");
                refreshTableList();
                // Update tabs
                for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                    Component c = tabbedPane.getComponentAt(i);
                    if (c instanceof TableTabPanel) {
                        TableTabPanel tab = (TableTabPanel) c;
                        if (tab.tableName.equals(oldName)) {
                            tab.tableName = newName;
                            tabbedPane.setTabComponentAt(i, createTabHeader(newName, tab));
                        }
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "Failed to rename table.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void dropSelectedTable() {
        String tableName = tableList.getSelectedValue();
        if (tableName == null) return;
        
        int res = JOptionPane.showConfirmDialog(this, "Are you sure you want to completely drop table '" + tableName + "'?\nThis action cannot be undone!", "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.YES_OPTION) {
            if (dao.dropTable(tableName)) {
                JOptionPane.showMessageDialog(this, "Table dropped.");
                refreshTableList();
                // Close tabs associated with this table
                for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
                    Component c = tabbedPane.getComponentAt(i);
                    if (c instanceof TableTabPanel) {
                        TableTabPanel tab = (TableTabPanel) c;
                        if (tab.tableName.equals(tableName)) {
                            tabbedPane.removeTabAt(i);
                        }
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "Failed to drop table.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void clearSearch() {
        TableTabPanel tab = getActiveTab();
        if (tab != null) {
            tab.currentSearchFilters.clear();
            tab.refreshTable();
        }
    }

    private void openSearchPanel() {
        TableTabPanel tab = getActiveTab();
        if (tab == null) return;
        
        List<String> columns = dao.getColumns(tab.tableName);
        JPanel panel = new JPanel(new GridLayout(columns.size(), 2, 5, 5));
        Map<String, JTextField> fields = new HashMap<>();
        
        for (String col : columns) {
            panel.add(new JLabel(col + ":"));
            JTextField tf = new JTextField();
            if (tab.currentSearchFilters.containsKey(col)) {
                tf.setText(tab.currentSearchFilters.get(col));
            }
            fields.put(col, tf);
            panel.add(tf);
        }

        int result = JOptionPane.showConfirmDialog(this, panel, "Advanced Search: " + tab.tableName, JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            tab.currentSearchFilters.clear();
            for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
                String val = entry.getValue().getText().trim();
                if (!val.isEmpty()) {
                    tab.currentSearchFilters.put(entry.getKey(), val);
                }
            }
            tab.refreshTable();
        }
    }

    private void openRecordPanel(boolean isEdit) {
        TableTabPanel tab = getActiveTab();
        if (tab == null) return;
        
        int selectedRow = tab.table.getSelectedRow();
        if (isEdit && selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a record to edit.");
            return;
        }

        List<String> columns = dao.getColumns(tab.tableName);
        JPanel panel = new JPanel(new GridLayout(columns.size(), 2, 5, 5));
        Map<String, JTextField> fields = new LinkedHashMap<>();
        Map<String, String> originalFilters = new HashMap<>();

        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i);
            panel.add(new JLabel(col + ":"));
            JTextField tf = new JTextField();
            if (isEdit) {
                Object val = tab.tableModel.getValueAt(selectedRow, i);
                String strVal = (val == null) ? "" : val.toString();
                tf.setText(strVal);
                originalFilters.put(col, strVal);
                if (col.equalsIgnoreCase("id")) tf.setEditable(false);
            } else if (col.equalsIgnoreCase("id")) {
                tf.setEditable(false);
                tf.setText("Auto");
            }
            fields.put(col, tf);
            panel.add(tf);
        }

        int result = JOptionPane.showConfirmDialog(this, panel, isEdit ? "Edit Record" : "Add Record", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            Map<String, String> data = new HashMap<>();
            for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
                String val = entry.getValue().getText().trim();
                if (!val.isEmpty() && !val.equals("Auto")) {
                    data.put(entry.getKey(), val);
                }
            }
            
            if (isEdit) {
                if (dao.updateRecord(tab.tableName, originalFilters, data)) {
                    JOptionPane.showMessageDialog(this, "Record updated.");
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to update record.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                if (dao.insertRecord(tab.tableName, data)) {
                    JOptionPane.showMessageDialog(this, "Record added.");
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to add record.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            refreshAllTabs(tab.tableName);
        }
    }

    private void deleteSelectedRecord() {
        TableTabPanel tab = getActiveTab();
        if (tab == null) return;
        
        int selectedRow = tab.table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a record to delete.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this record?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            List<String> columns = dao.getColumns(tab.tableName);
            Map<String, String> filters = new HashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                Object val = tab.tableModel.getValueAt(selectedRow, i);
                if (val != null) filters.put(columns.get(i), val.toString());
            }

            if (dao.deleteRecord(tab.tableName, filters)) {
                JOptionPane.showMessageDialog(this, "Record deleted.");
                refreshAllTabs(tab.tableName);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete record.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void refreshAllTabs(String tableName) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component c = tabbedPane.getComponentAt(i);
            if (c instanceof TableTabPanel) {
                TableTabPanel t = (TableTabPanel) c;
                if (t.tableName.equals(tableName)) {
                    t.refreshTable();
                }
            }
        }
    }

    private void openCreateTableDialog() {
        String tableName = JOptionPane.showInputDialog(this, "Enter new table name:");
        if (tableName == null || tableName.trim().isEmpty()) return;

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        JPanel columnsPanel = new JPanel(new GridLayout(0, 3, 5, 5));
        columnsPanel.add(new JLabel("Column Name"));
        columnsPanel.add(new JLabel("Data Type"));
        columnsPanel.add(new JLabel("Constraints (e.g. PRIMARY KEY)"));

        List<JTextField> nameFields = new ArrayList<>();
        List<JComboBox<String>> typeBoxes = new ArrayList<>();
        List<JTextField> constraintFields = new ArrayList<>();

        JButton addRowBtn = new JButton("Add Column");
        addRowBtn.addActionListener(e -> {
            JTextField nameField = new JTextField();
            JComboBox<String> typeBox = new JComboBox<>(new String[]{"INTEGER", "TEXT", "REAL", "BLOB"});
            JTextField constraintField = new JTextField();
            
            nameFields.add(nameField);
            typeBoxes.add(typeBox);
            constraintFields.add(constraintField);
            
            columnsPanel.add(nameField);
            columnsPanel.add(typeBox);
            columnsPanel.add(constraintField);
            mainPanel.revalidate();
            mainPanel.repaint();
        });

        // Add initial row
        addRowBtn.doClick();

        JScrollPane scrollPane = new JScrollPane(columnsPanel);
        scrollPane.setPreferredSize(new Dimension(600, 300));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane);
        mainPanel.add(addRowBtn);

        int result = JOptionPane.showConfirmDialog(this, mainPanel, "Create Table: " + tableName, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            Map<String, String> columnDefs = new LinkedHashMap<>();
            for (int i = 0; i < nameFields.size(); i++) {
                String name = nameFields.get(i).getText().trim();
                if (name.isEmpty()) continue;
                
                String type = (String) typeBoxes.get(i).getSelectedItem();
                String constraints = constraintFields.get(i).getText().trim();
                
                columnDefs.put(name, type + " " + constraints);
            }
            
            if (dao.createTable(tableName, columnDefs)) {
                JOptionPane.showMessageDialog(this, "Table '" + tableName + "' created successfully!");
                refreshTableList();
                openTableInTab(tableName, true);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to create table.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Inner class representing a tab
    class TableTabPanel extends JPanel {
        String tableName;
        JTable table;
        DefaultTableModel tableModel;
        Map<String, String> currentSearchFilters = new HashMap<>();

        public TableTabPanel(String tableName) {
            this.tableName = tableName;
            setLayout(new BorderLayout());
            tableModel = new DefaultTableModel();
            table = new JTable(tableModel);
            add(new JScrollPane(table), BorderLayout.CENTER);
            refreshTable();
        }

        public void refreshTable() {
            List<String> columns = dao.getColumns(tableName);
            tableModel.setColumnIdentifiers(columns.toArray());
            tableModel.setRowCount(0);

            List<Map<String, Object>> records = dao.searchRecords(tableName, currentSearchFilters);
            for (Map<String, Object> row : records) {
                Object[] rowData = new Object[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    rowData[i] = row.get(columns.get(i));
                }
                tableModel.addRow(rowData);
            }
        }
    }

    class SqlEditorTabPanel extends JPanel {
        JTextArea editorArea;
        JTabbedPane resultTabs;
        JTextArea messageArea;

        public SqlEditorTabPanel() {
            setLayout(new BorderLayout());
            
            JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton execBtn = new JButton("Execute All");
            JButton clearBtn = new JButton("Clear");
            topBar.add(execBtn);
            topBar.add(clearBtn);
            
            editorArea = new JTextArea();
            editorArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
            
            resultTabs = new JTabbedPane();
            messageArea = new JTextArea();
            messageArea.setEditable(false);
            messageArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            resultTabs.addTab("Messages", new JScrollPane(messageArea));
            
            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(editorArea), resultTabs);
            splitPane.setResizeWeight(0.6);
            
            add(topBar, BorderLayout.NORTH);
            add(splitPane, BorderLayout.CENTER);
            
            clearBtn.addActionListener(e -> editorArea.setText(""));
            execBtn.addActionListener(e -> executeScript());
        }
        
        private void executeScript() {
            String script = editorArea.getText().trim();
            if (script.isEmpty()) return;
            
            while (resultTabs.getTabCount() > 1) {
                resultTabs.removeTabAt(1);
            }
            messageArea.setText("Executing script...\n");
            
            List<DynamicDAO.QueryResult> results = dao.executeRawScript(script);
            int resultIndex = 1;
            for (int i = 0; i < results.size(); i++) {
                DynamicDAO.QueryResult res = results.get(i);
                if (res.message != null && !res.message.isEmpty()) {
                    messageArea.append("Query " + (i+1) + ": " + res.message + "\n");
                } else if (res.isResultSet) {
                    messageArea.append("Query " + (i+1) + ": Returned " + res.rows.size() + " rows.\n");
                    
                    DefaultTableModel tm = new DefaultTableModel();
                    tm.setColumnIdentifiers(res.columns.toArray());
                    for (Map<String, Object> r : res.rows) {
                        Object[] rowData = new Object[res.columns.size()];
                        for (int c = 0; c < res.columns.size(); c++) {
                            rowData[c] = r.get(res.columns.get(c));
                        }
                        tm.addRow(rowData);
                    }
                    JTable t = new JTable(tm);
                    resultTabs.addTab("Result " + resultIndex++, new JScrollPane(t));
                } else {
                    messageArea.append("Query " + (i+1) + ": " + res.affectedRows + " rows affected.\n");
                }
            }
            refreshTableList();
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                Component c = tabbedPane.getComponentAt(i);
                if (c instanceof TableTabPanel) {
                    ((TableTabPanel) c).refreshTable();
                }
            }
        }
    }

    private boolean isMcpRunning() {
        try {
            File statusFile = McpServerMain.getStatusFile();
            if (statusFile != null && statusFile.exists()) {
                String content = Files.readString(statusFile.toPath()).trim();
                if (!content.isEmpty()) {
                    long pid = Long.parseLong(content);
                    return ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isPresent();
                }
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // Custom Icon that paints a bright, crisp anti-aliased circular status dot
    private static class StatusDotIcon implements Icon {
        private final Color color;
        private final int size;

        public StatusDotIcon(Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Subtle outer glow/halo
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
            g2.fillOval(x, y, size + 2, size + 2);
            // Core colored dot
            g2.setColor(color);
            g2.fillOval(x + 1, y + 1, size, size);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size + 4;
        }

        @Override
        public int getIconHeight() {
            return size + 4;
        }
    }
}
