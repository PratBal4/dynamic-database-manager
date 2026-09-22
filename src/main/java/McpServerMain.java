import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class McpServerMain {

    private static DynamicDAO dao;

    public static void main(String[] args) {
        System.out.println("Starting MCP server...");

        String dbPath = resolveDbPath(args);
        dao = new DynamicDAO(dbPath);

        registerStatusIndicator();

        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("dynamic-db-mcp-server", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .build();

        server.addTool(listTablesTool());
        server.addTool(getColumnsTool());
        server.addTool(insertRecordTool());
        server.addTool(runQueryTool());

        System.out.println("MCP server started");
    }

    private static String resolveDbPath(String[] args) {
        if (args != null && args.length > 0 && !args[0].trim().isEmpty()) {
            return args[0].trim();
        }
        String sysProp = System.getProperty("db.path");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp.trim();
        }
        // Check relative to current working directory
        File rel = new File("database/test.db");
        if (rel.exists()) {
            return rel.getAbsolutePath();
        }
        // Check relative to JAR location (parent of target/)
        try {
            File codeSource = new File(McpServerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File parent = codeSource.getParentFile();
            if (parent != null) {
                File candidate1 = new File(parent, "database/test.db");
                if (candidate1.exists()) return candidate1.getAbsolutePath();
                File grandParent = parent.getParentFile();
                if (grandParent != null) {
                    File candidate2 = new File(grandParent, "database/test.db");
                    if (candidate2.exists()) return candidate2.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {}

        // Fallback to default user path if available
        File fallback = new File("C:\\Users\\malin\\OneDrive\\Desktop\\Student management system\\dynamic-database-manager\\database\\test.db");
        if (fallback.exists()) {
            return fallback.getAbsolutePath();
        }
        return "database/test.db";
    }

    private static void registerStatusIndicator() {
        try {
            File statusFile = getStatusFile();
            if (statusFile.getParentFile() != null) {
                statusFile.getParentFile().mkdirs();
            }
            long pid = ProcessHandle.current().pid();
            Files.writeString(statusFile.toPath(), String.valueOf(pid));
            statusFile.deleteOnExit();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    statusFile.delete();
                } catch (Exception ignored) {}
            }));
        } catch (Exception ignored) {}
    }

    public static File getStatusFile() {
        File dbDir = new File("database");
        if (dbDir.exists()) {
            return new File(dbDir, ".mcp_running");
        }
        try {
            File codeSource = new File(McpServerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File parent = codeSource.getParentFile();
            if (parent != null && parent.getParentFile() != null) {
                File candidate = new File(parent.getParentFile(), "database/.mcp_running");
                if (candidate.getParentFile().exists()) return candidate;
            }
        } catch (Exception ignored) {}
        return new File(System.getProperty("java.io.tmpdir"), ".dynamic_db_mcp_running");
    }

    private static SyncToolSpecification listTablesTool() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
        );

        return SyncToolSpecification.builder()
                .tool(Tool.builder("list_tables", inputSchema)
                        .description("List all tables in the database")
                        .build())
                .callHandler((exchange, request) -> {
                    List<String> tables = dao.getTables();
                    return CallToolResult.builder()
                            .addTextContent(String.join(", ", tables))
                            .build();
                })
                .build();
    }

    private static SyncToolSpecification getColumnsTool() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "tableName", Map.of("type", "string", "description", "Name of the table")
                ),
                "required", List.of("tableName")
        );

        return SyncToolSpecification.builder()
                .tool(Tool.builder("get_columns", inputSchema)
                        .description("Get the column names of a table")
                        .build())
                .callHandler((exchange, request) -> {
                    String tableName = (String) request.arguments().get("tableName");
                    List<String> columns = dao.getColumns(tableName);
                    return CallToolResult.builder()
                            .addTextContent(String.join(", ", columns))
                            .build();
                })
                .build();
    }

    private static SyncToolSpecification insertRecordTool() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "tableName", Map.of("type", "string", "description", "Name of the table"),
                        "data", Map.of("type", "object", "description", "Column-value pairs to insert")
                ),
                "required", List.of("tableName", "data")
        );

        return SyncToolSpecification.builder()
                .tool(Tool.builder("insert_record", inputSchema)
                        .description("Insert a new record into a table")
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        String tableName = (String) request.arguments().get("tableName");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rawData = (Map<String, Object>) request.arguments().get("data");

                        if (tableName == null || rawData == null) {
                            return CallToolResult.builder()
                                    .addTextContent("Error: tableName and data are required arguments.")
                                    .build();
                        }

                        Map<String, String> data = new HashMap<>();
                        for (Map.Entry<String, Object> entry : rawData.entrySet()) {
                            data.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }

                        boolean success = dao.insertRecord(tableName, data);
                        return CallToolResult.builder()
                                .addTextContent(success ? "Record inserted successfully into '" + tableName + "'." : "Failed to insert record.")
                                .build();
                    } catch (Exception e) {
                        return CallToolResult.builder()
                                .addTextContent("Error inserting record: " + e.getMessage())
                                .build();
                    }
                })
                .build();
    }

    private static SyncToolSpecification runQueryTool() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "sql", Map.of("type", "string", "description", "Raw SQL to execute")
                ),
                "required", List.of("sql")
        );

        return SyncToolSpecification.builder()
                .tool(Tool.builder("run_query", inputSchema)
                        .description("Execute a raw SQL script against the database")
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        String sql = (String) request.arguments().get("sql");
                        if (sql == null || sql.trim().isEmpty()) {
                            return CallToolResult.builder()
                                    .addTextContent("Error: 'sql' argument is empty or missing.")
                                    .build();
                        }

                        List<DynamicDAO.QueryResult> results = dao.executeRawScript(sql);

                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < results.size(); i++) {
                            DynamicDAO.QueryResult r = results.get(i);
                            if (r.message != null && !r.message.isEmpty()) {
                                sb.append("Query ").append(i + 1).append(" Error: ").append(r.message).append("\n");
                            } else if (r.isResultSet) {
                                sb.append("Returned ").append(r.rows.size()).append(" rows:\n");
                                for (Map<String, Object> row : r.rows) {
                                    sb.append(row.toString()).append("\n");
                                }
                            } else {
                                sb.append(r.affectedRows).append(" rows affected.\n");
                            }
                        }

                        String output = sb.toString().trim();
                        if (output.isEmpty()) {
                            output = "Query executed successfully (no rows returned).";
                        }

                        return CallToolResult.builder()
                                .addTextContent(output)
                                .build();
                    } catch (Exception e) {
                        return CallToolResult.builder()
                                .addTextContent("Error executing query: " + e.getClass().getSimpleName() + ": " + e.getMessage())
                                .build();
                    }
                })
                .build();
    }
}