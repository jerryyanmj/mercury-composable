package home.jerry.test.jooq.program;

import org.jooq.Param;

import java.util.List;
import java.util.Map;

public class SqlCommand {
    private final String sql;
    private final List<Object> bindValues;

    public SqlCommand(String sql, List<Object> bindValues) {
        this.sql = sql;
        this.bindValues = bindValues;
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getBindValues() {
        return bindValues;
    }

    @Override
    public String toString() {
        return "SqlCommand{" +
                "sql='" + sql + '\'' +
                ", bindValues=" + bindValues +
                '}';
    }
}
