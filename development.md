# Development Progress & Technical Notes

This document captures development progress, technical architecture decisions, build instructions, and debugging notes for the **Dynamic Database Manager** project, specifically focusing on the **MCP (Model Context Protocol) Server** integration branch (`mcp-server-support`).

---

## 1. Project Overview & Tech Stack

* **Language**: Java 17
* **Build Tool**: Apache Maven (migrated from legacy manual `lib/*.jar` setup)
* **Database**: SQLite via `org.xerial:sqlite-jdbc` (3.46.0.0)
* **MCP SDK**: `io.modelcontextprotocol.sdk:mcp` (2.0.0) with Jackson 3 JSON mapping
* **UI Framework**: Java Swing with FlatLaf modern dark look-and-feel
* **Client**: Tested against Claude Desktop for Windows

---

## 2. MCP Server Implementation

The MCP server entry point is [`McpServerMain.java`](file:///src/main/java/McpServerMain.java). It wraps [`DynamicDAO.java`](file:///src/main/java/DynamicDAO.java) and communicates with MCP clients (like Claude Desktop) over Standard I/O (`stdio`).

### Exposed Tools:

| Tool Name | Parameters | Description |
| :--- | :--- | :--- |
| `list_tables` | None | Returns a comma-separated list of all user tables in the SQLite database. |
| `get_columns` | `tableName` (string) | Returns the column names for a given table using SQLite's `PRAGMA table_info`. |
| `insert_record`| `tableName` (string), `data` (object) | Inserts a new record into the specified table using key-value pairs. |
| `run_query` | `sql` (string) | Executes raw SQL scripts/queries and returns tabular results or affected row counts. |

---

## 3. How to Build and Run

### Running the GUI (Default)
```powershell
mvn compile exec:java
```

### Running the MCP Server via Maven
PowerShell parses `-D` parameters as special options, so the stop-parsing token (`--%`) is mandatory in PowerShell:
```powershell
mvn --% compile exec:java -Dexec.mainClass=McpServerMain
```

### Building the Runnable Shaded Fat JAR
To create the standalone shaded JAR containing all dependencies (used by Claude Desktop):
```powershell
mvn jar:jar shade:shade
```
*(Or `mvn package` if no other processes are locking build directories).*

The generated JAR is located at:
`target/dynamic-database-manager-1.0.0.jar`

---

## 4. Claude Desktop Integration

Claude Desktop runs the MCP server as a background subprocess using standard I/O.

### Configuration File Location (Windows):
* Standard install: `%APPDATA%\Claude\claude_desktop_config.json`
* Windows Store / MSIX package:
  `%LOCALAPPDATA%\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Roaming\Claude\claude_desktop_config.json`

### Configuration Content:
```json
{
  "mcpServers": {
    "dynamic-database-manager": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\Users\\malin\\OneDrive\\Desktop\\Student management system\\dynamic-database-manager\\target\\dynamic-database-manager-1.0.0.jar"
      ]
    }
  }
}
```

---

## 5. Major Bugs Solved & Debugging Notes

### Bug 1: The `run_query` Empty Result Bug
* **Symptom**: `run_query` returned completely blank responses in Claude Desktop, even for `SELECT 1 AS test` or queries on populated tables.
* **Root Cause**:
  In [`DynamicDAO.java`](file:///src/main/java/DynamicDAO.java), `QueryResult.message` is initialized to `""` (empty string).
  In [`McpServerMain.java`](file:///src/main/java/McpServerMain.java), the query response loop was written as:
  ```java
  if (r.message != null) {
      sb.append(r.message).append("\n");
  } else if (r.isResultSet) {
      // Row formatting was here!
  }
  ```
  In Java, `"" != null` evaluates to **true**! Every query matched `if (r.message != null)` and appended `"" + "\n"`, completely skipping the row rendering code.
* **Solution**: Updated condition to match the CLI/GUI pattern:
  ```java
  if (r.message != null && !r.message.isEmpty()) { ... }
  ```

### Bug 2: Stale JARs & Windows File Locks
* **Symptom**: Code changes appeared not to take effect in Claude Desktop.
* **Root Cause**:
  1. Running `mvn compile` only compiles classes in `target/classes`; it does not update the JAR file in `target/`.
  2. While Claude Desktop is running, Windows keeps an exclusive handle on `target/dynamic-database-manager-1.0.0.jar`.
  3. OneDrive can briefly lock `.class` files when they are regenerated in the `Desktop/` folder.
* **Solution**: Fully close Claude Desktop before rebuilding the JAR, or use `mvn jar:jar shade:shade`.

### Bug 3: Hardcoded File Paths
* **Symptom**: `McpServerMain.java` contained a hardcoded path (`C:\Users\malin\...`) that would break for any collaborator.
* **Solution**: Added `resolveDbPath(args)` to resolve `database/test.db` dynamically from CLI arguments, system properties, current working directory, or relative to the JAR location.

---

## 6. GUI MCP Server Status Indicator

To meet project requirements, a live status badge was added to the top-right toolbar of [`DynamicDBManagerGUI.java`](file:///src/main/java/DynamicDBManagerGUI.java):
* `🟢 MCP Server: Online`: Displayed when Claude Desktop or CLI has an active MCP server instance running.
* `⚪ MCP Server: Offline`: Displayed when no MCP server is running.

**Mechanism**:
* [`McpServerMain.java`](file:///src/main/java/McpServerMain.java) registers a temporary `.mcp_running` lock file containing its process ID (PID) and cleans it up via a JVM shutdown hook and `deleteOnExit()`.
* [`DynamicDBManagerGUI.java`](file:///src/main/java/DynamicDBManagerGUI.java) runs a lightweight 2-second Swing `Timer` that verifies the PID is active using `ProcessHandle.of(pid).isAlive()`.

---

## 7. Git & Pull Request Instructions

To push the changes to GitHub and submit the Pull Request:
```powershell
# 1. Stage all changes
git add .

# 2. Commit with a descriptive message
git commit -m "feat: complete MCP server support with run_query fix and GUI status indicator"

# 3. Push the branch to origin
git push -u origin mcp-server-support

# 4. Open GitHub and create a Pull Request from mcp-server-support into main
```
