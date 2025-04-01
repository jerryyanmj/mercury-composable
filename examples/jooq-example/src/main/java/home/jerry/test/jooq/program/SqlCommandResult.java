package home.jerry.test.jooq.program;

import java.util.Map;

public class SqlCommandResult {
    private final SqlCommand sqlCommand;
    private Map<String, Object> commandResult;
    private Throwable error;

    public SqlCommandResult(SqlCommand sqlCommand, Map<String, Object> commandResult) {
        this.sqlCommand = sqlCommand;
        this.commandResult = commandResult;
    }

    public SqlCommandResult(SqlCommand sqlCommand, Throwable error) {
        this.sqlCommand = sqlCommand;
        this.error = error;
    }

    @Override
    public String toString() {
        return "SqlCommandResult{" +
                "sqlCommand=" + sqlCommand +
                ", commandResult=" + commandResult +
                ", error=" + error +
                '}';
    }
}
