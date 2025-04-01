package home.jerry.test.r2dbc;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import org.platformlambda.core.models.TypedLambdaFunction;
import org.platformlambda.core.util.Utility;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DbService implements TypedLambdaFunction<Map<String, Object>, Object> {
    private static final Utility util = Utility.getInstance();
    private static final String SQL = "sql";
    private static final String VALUES = "values";
    private static final String EMPTY = "empty";
    private static final String BIND = "bind";
    private final ConnectionPool pool;

    public DbService(ConnectionPool pool) {
        this.pool = pool;
    }

    /**
     * handle event
     * <p>
     * headers:
     * "bind" (default is none) = "values", "empty" or "none"
     * <p>
     * Input contract
     * "sql" - SQL statement in a text string
     * "values" (Optional) -
     *    When bind=values, binding values in a map of key-values where a key can be index number or name.
     *    When bind=empty, bind keys as null values where each object type is text, bytes, boolean or integer.
     *    When bind=none, the "values" map will be ignored.
     * <p>
     * Index number starts from 0 and index name ($) starts from 1
     *
     * @param headers of event
     * @param input of event
     * @param instance of the worker
     * @return mono reactive result
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object handleEvent(Map<String, String> headers, Map<String, Object> input, int instance) {
        var statement = input.get(SQL);
        var values = input.get(VALUES);
        var map = values instanceof Map? (Map<String, Object>) values : Collections.EMPTY_MAP;
        var bindType = String.valueOf(headers.getOrDefault(BIND, "none"));
        if (statement instanceof String) {
            return executeSql(String.valueOf(statement), bindType, map);
        } else {
            throw new IllegalArgumentException("Missing SQL statement");
        }
    }

    private boolean isBindByIndex(List<String> keys) {
        int numbers = 0;
        int names = 0;
        for (String k: keys) {
            if (k.startsWith("$")) {
                if (util.isDigits(k.substring(1))) {
                    names++;
                }
            } else if (util.isDigits(k)) {
                numbers++;
            }
        }
        if (numbers == keys.size()) {
            return true;
        } else if (names == keys.size()) {
            return false;
        } else {
            throw new RuntimeException("Parameters must be n or $n where n is a number");
        }
    }

    private void updatePrepareStatement(String bindType, Statement prepareStatement, Map<String, Object> values) {
        if (!values.isEmpty()) {
            List<String> keys = new ArrayList<>(values.keySet());
            if (VALUES.equals(bindType)) {
                var bindByIndex = isBindByIndex(keys);
                for (String k : keys) {
                    if (bindByIndex) {
                        int index = util.str2int(k);
                        if (index >= 0) {
                            prepareStatement.bind(index, values.get(k));
                        } else {
                            throw new RuntimeException("Bind index must start from 0. Actual: " + k);
                        }
                    } else {
                        prepareStatement.bind(k, values.get(k));
                    }
                }
            }
            if (EMPTY.equals(bindType)) {
                for (String k : keys) {
                    var clsType = String.valueOf(values.get(k));
                    final Class<?> cls = switch (clsType) {
                        case "text" -> String.class;
                        case "bytes" -> byte[].class;
                        case "boolean" -> Boolean.class;
                        case "integer" -> Integer.class;
                        case null, default -> throw new RuntimeException("Class for null value binding must be " +
                                "text, bytes, boolean or integer. Actual: " + k);
                    };
                    var bindByIndex = isBindByIndex(keys);
                    if (bindByIndex) {
                        int index = util.str2int(k);
                        if (index >= 0) {
                            prepareStatement.bindNull(index, cls);
                        } else {
                            throw new RuntimeException("Bind index must start from 0. Actual: " + k);
                        }
                    } else {
                        prepareStatement.bindNull(k, cls);
                    }
                }
            }
        }
    }

    private Mono<Map<String, Object>> executeSql(String statement,
                                                 String bindType, Map<String, Object> values) {
        return Mono.create(emitter -> {
            var done = new AtomicBoolean(false);
            var ref = new AtomicReference<Connection>();
            Flux<? extends Result> db = pool.create().flatMapMany(connection -> {
                        ref.set(connection);
                        var prepareStatement = connection.createStatement(statement);
                        updatePrepareStatement(bindType, prepareStatement, values);
                        return prepareStatement.execute();
                    })
                    .doFinally(signal -> Mono.from(ref.get().close()).subscribe());
            db.subscribe(result -> Mono.from(result.getRowsUpdated()).doFinally(ok -> {
                if (!done.get()) {
                    var records = new ArrayList<Map<String, Object>>();
                    // retrieve records
                    db.flatMap(rows ->
                            rows.map((row, rowMetadata) -> {
                                var record = new HashMap<String, Object>();
                                rowMetadata.getColumnMetadatas().forEach(column ->
                                        record.put(column.getName().toLowerCase(), row.get(column.getName())));
                                return record;
                            })).subscribe(records::add, emitter::error,
                            () -> emitter.success(Map.of("total", records.size(), "records", records)));
                }
            }).subscribe(n -> {
                done.set(true);
                emitter.success(Map.of("row_updated", n));
            }), e -> {
                done.set(true);
                emitter.error(e);
            });
        });
    }
}