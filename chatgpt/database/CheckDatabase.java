import java.sql.*;

/** Read-only preflight. Never prints the URL, user, password, or note contents. */
class CheckDatabase {
  public static void main(String[] args) {
    DriverManager.setLoginTimeout(20);
    try (Connection connection = DriverManager.getConnection(
        System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
      try (Statement statement = connection.createStatement()) {
        statement.setQueryTimeout(20);
        try (ResultSet result = statement.executeQuery("SELECT 1 FROM dual")) {
          if (!result.next() || result.getInt(1) != 1) throw new SQLException("Probe failed");
        }
        System.out.println("DATABASE_CONNECTED=true");
        try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM user_tables WHERE table_name = 'CHATGPT_NOTES'")) {
          result.next();
          System.out.println("NOTES_TABLE_EXISTS=" + (result.getInt(1) == 1));
        }
      }
    } catch (SQLException error) {
      System.out.println("DATABASE_CONNECTED=false");
      System.out.println("ORACLE_ERROR_CODE=" + error.getErrorCode());
      System.out.println("SQL_STATE=" + error.getSQLState());
      System.exit(1);
    }
  }
}
