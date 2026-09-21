import java.io.File;
import java.sql.*;
import java.util.*;

public class DynamicDAO {
    private String dbPath;

    public static class QueryResult {
        public boolean isResultSet = false;
        public List<String> columns = new ArrayList<>();
        public List<Map<String, Object>> rows = new ArrayList<>();
        public int affectedRows = 0;
        public String message = "";
    }

    public DynamicDAO(String dbPath) {
        this.dbPath = dbPath;
    }

    public List<QueryResult> executeRawScript(String script) {
        List<QueryResult> results = new ArrayList<>();
        String[] queries = script.split(";");
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            for (String q : queries) {
                String sql = q.trim();
                if (sql.isEmpty()) continue;
                
                QueryResult result = new QueryResult();
                try {
                    boolean hasResultSet = stmt.execute(sql);
                    if (hasResultSet) {
                        result.isResultSet = true;
                        try (ResultSet rs = stmt.getResultSet()) {
                            ResultSetMetaData rsmd = rs.getMetaData();
                            int columnCount = rsmd.getColumnCount();
                            for (int i = 1; i <= columnCount; i++) {
                                result.columns.add(rsmd.getColumnName(i));
                            }
                            while (rs.next()) {
                                Map<String, Object> row = new LinkedHashMap<>();
                                for (String col : result.columns) {
                                    row.put(col, rs.getObject(col));
                                }
                                result.rows.add(row);
                            }
                        }
                    } else {
                        result.affectedRows = stmt.getUpdateCount();
                    }
                } catch (SQLException e) {
                    result.message = e.getMessage();
                }
                results.add(result);
            }
        } catch (SQLException e) {
            QueryResult err = new QueryResult();
            err.message = "Connection Error: " + e.getMessage();
            results.add(err);
        }
        return results;
    }

    private Connection connect() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public boolean createTable(String tableName, Map<String, String> columnDefinitions) {
        if (columnDefinitions.isEmpty()) return false;
        
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        int count = 0;
        for (Map.Entry<String, String> entry : columnDefinitions.entrySet()) {
            if (count > 0) sql.append(", ");
            sql.append(entry.getKey()).append(" ").append(entry.getValue());
            count++;
        }
        sql.append(");");

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
            return true;
        } catch (SQLException e) {
            System.err.println("Error creating table: " + e.getMessage());
            return false;
        }
    }

    public boolean dropTable(String tableName) {
        String sql = "DROP TABLE IF EXISTS " + tableName;
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (SQLException e) {
            System.err.println("Error dropping table: " + e.getMessage());
            return false;
        }
    }

    public boolean renameTable(String oldName, String newName) {
        String sql = "ALTER TABLE " + oldName + " RENAME TO " + newName;
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (SQLException e) {
            System.err.println("Error renaming table: " + e.getMessage());
            return false;
        }
    }

    public List<String> getTables() {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tables;
    }

    public List<String> getColumns(String tableName) {
        List<String> columns = new ArrayList<>();
        String sql = "PRAGMA table_info(" + tableName + ")";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return columns;
    }

    public List<Map<String, Object>> getAllRecords(String tableName) {
        return searchRecords(tableName, Collections.emptyMap());
    }

    public List<Map<String, Object>> searchRecords(String tableName, Map<String, String> filters) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<String> columns = getColumns(tableName);
        if (columns.isEmpty()) return results;

        StringBuilder sql = new StringBuilder("SELECT * FROM " + tableName);
        List<String> values = new ArrayList<>();

        if (!filters.isEmpty()) {
            sql.append(" WHERE ");
            int count = 0;
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                if (count > 0) sql.append(" AND ");
                sql.append(entry.getKey()).append(" LIKE ?");
                values.add("%" + entry.getValue() + "%");
                count++;
            }
        }

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < values.size(); i++) {
                pstmt.setString(i + 1, values.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String col : columns) {
                        row.put(col, rs.getObject(col));
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    public boolean insertRecord(String tableName, Map<String, String> data) {
        if (data.isEmpty()) return false;
        
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        StringBuilder placeholders = new StringBuilder(" VALUES (");
        
        List<String> values = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (count > 0) {
                sql.append(", ");
                placeholders.append(", ");
            }
            sql.append(entry.getKey());
            placeholders.append("?");
            values.add(entry.getValue());
            count++;
        }
        sql.append(")").append(placeholders).append(")");

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) {
                pstmt.setString(i + 1, values.get(i));
            }
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateRecord(String tableName, Map<String, String> filters, Map<String, String> updates) {
        if (updates.isEmpty() || filters.isEmpty()) return false;
        
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        List<String> values = new ArrayList<>();
        
        int count = 0;
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            if (count > 0) sql.append(", ");
            sql.append(entry.getKey()).append(" = ?");
            values.add(entry.getValue());
            count++;
        }

        sql.append(" WHERE ");
        count = 0;
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            if (count > 0) sql.append(" AND ");
            sql.append(entry.getKey()).append(" = ?");
            values.add(entry.getValue());
            count++;
        }

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) {
                pstmt.setString(i + 1, values.get(i));
            }
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteRecord(String tableName, Map<String, String> filters) {
        if (filters.isEmpty()) return false; // Prevent accidental wipe

        StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName).append(" WHERE ");
        List<String> values = new ArrayList<>();
        
        int count = 0;
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            if (count > 0) sql.append(" AND ");
            sql.append(entry.getKey()).append(" = ?");
            values.add(entry.getValue());
            count++;
        }

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) {
                pstmt.setString(i + 1, values.get(i));
            }
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
