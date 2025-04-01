package home.jerry.test.jooq.composable;

import home.jerry.test.jooq.program.SqlCommand;
import home.jerry.test.jooq.program.SqlCommandResult;
import io.r2dbc.spi.*;
import org.platformlambda.core.annotations.PreLoad;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.models.TypedLambdaFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

import static home.jerry.test.jooq.composable.SqlLambdaFunction.V1_DATA_SQL;

@PreLoad(route = V1_DATA_SQL)
public class SqlLambdaFunction implements TypedLambdaFunction<EventEnvelope, Object> {

    public static final String V1_DATA_SQL = "v1.data.sql";

    @Override
    public Object handleEvent(Map<String, String> headers, EventEnvelope input, int instance) throws Exception {
        var op = headers.get("op");
        return switch (op) {
            case "insert", "update", "delete", "ddl" ->  handleSingleReturnStatement(headers, input, instance);
            default -> handleDataReturnStatement(headers, input, instance);
        };
    }

    private Mono<SqlCommandResult> handleSingleReturnStatement(Map<String, String> headers, EventEnvelope input, int instance) {

        ConnectionFactory connectionFactory = ConnectionFactories.get(
                ConnectionFactoryOptions
                        .parse("r2dbc:mysql://localhost:3306/Token")
                        .mutate()
                        .option(ConnectionFactoryOptions.USER, "root")
                        .option(ConnectionFactoryOptions.PASSWORD, "password")
                        .build()
        );

        var sqlCommand = input.getBody(SqlCommand.class);
        var sql = sqlCommand.getSql();
        var bindValues = sqlCommand.getBindValues();

        return Flux.usingWhen(
                        connectionFactory.create(),
                        c -> {
                            var stmt = c.createStatement(sql);
                            for (int i = 0; i < bindValues.size(); i++) {
                                stmt = stmt.bind(i, bindValues.get(i));
                            }
                            return stmt.execute();
                        },
                        Connection::close
                ).flatMap(result -> result.getRowsUpdated()).map(aLong -> Map.of("ROWS_UPDATED", (Object) aLong))
                .map(stringObjectMap -> new SqlCommandResult(sqlCommand, stringObjectMap))
                .onErrorResume(throwable -> Flux.just(new SqlCommandResult(sqlCommand, throwable))).take(1).single();
    }

    private Flux<SqlCommandResult> handleDataReturnStatement(Map<String, String> headers, EventEnvelope input, int instance) {

        ConnectionFactory connectionFactory = ConnectionFactories.get(
                ConnectionFactoryOptions
                        .parse("r2dbc:mysql://localhost:3306/Token")
                        .mutate()
                        .option(ConnectionFactoryOptions.USER, "root")
                        .option(ConnectionFactoryOptions.PASSWORD, "password")
                        .build()
        );

        var sqlCommand = input.getBody(SqlCommand.class);
        var sql = sqlCommand.getSql();
        var bindValues = sqlCommand.getBindValues();

        return Flux.usingWhen(
                        connectionFactory.create(),
                        c -> {
                            var stmt = c.createStatement(sql);
                            for (int i = 0; i < bindValues.size(); i++) {
                                stmt = stmt.bind(i, bindValues.get(i));
                            }
                            return stmt.execute();
                        },
                        Connection::close
                ).flatMap(result -> result.map((r, m) -> m.getColumnMetadatas().stream().collect(Collectors.toMap(ReadableMetadata::getName, v -> r.get(v.getName())))))
                .map(stringObjectMap -> new SqlCommandResult(sqlCommand, stringObjectMap))
                .onErrorResume(throwable -> Flux.just(new SqlCommandResult(sqlCommand, throwable)));

    }
}
