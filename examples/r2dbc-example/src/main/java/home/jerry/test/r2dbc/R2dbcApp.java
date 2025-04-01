package home.jerry.test.r2dbc;

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.asyncer.r2dbc.mysql.api.MySqlResult;
import io.asyncer.r2dbc.mysql.constant.SslMode;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class R2dbcApp {

    private static final Logger log = LoggerFactory.getLogger(R2dbcApp.class);

    public static void main(String[] args) throws InterruptedException {
        var latch = new CountDownLatch(1);

        ConnectionFactoryOptions connectionFactoryOptions = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.HOST, "127.0.0.1")
                .option(ConnectionFactoryOptions.DATABASE, "Token")
                .option(ConnectionFactoryOptions.USER, "root")
                .option(ConnectionFactoryOptions.PASSWORD, "password")
                .option(ConnectionFactoryOptions.PROTOCOL, "pool")
                .option(ConnectionFactoryOptions.SSL, false).build();
//        ConnectionFactory connectionFactory = ConnectionFactories.get(connectionFactoryOptions);

        ConnectionFactory mySqlConnectionFactory = MySqlConnectionFactory.from(
                MySqlConnectionConfiguration.builder()
                        .host("127.0.0.1")
                        .database("Token")
                        .user("root")
                        .password("password")
                        .sslMode(SslMode.DISABLED)
                        .build());

        ConnectionPoolConfiguration connectionPoolConfiguration = ConnectionPoolConfiguration
                .builder()
                .connectionFactory(mySqlConnectionFactory)
                .initialSize(1)
                .maxSize(5)
                .build();

        ConnectionPool connectionPool = new ConnectionPool(connectionPoolConfiguration);

        var dropStmt = "DROP TABLE IF EXISTS `test_table`;";
        var createStmt = "CREATE TABLE `test_table`(id SERIAL, first_name VARCHAR(255), last_name VARCHAR(255));";
        var insertStmt = "INSERT INTO `customers` (first_name, last_name) VALUES (?,?), (?, ?);";
        var selectStmt = "SELECT * FROM `customers`;";

//        connectionPool.create().flatMapMany(connection -> connection.createStatement(dropStmt).execute())
//                .flatMap(Result::getRowsUpdated)
//                .subscribe(result -> log.info("delete rows updated: {}", result));
//
//        connectionPool.create().flatMapMany(connection -> connection.createStatement(createStmt).execute())
//                .flatMap(Result::getRowsUpdated)
//                .subscribe(result -> log.info("create rows updated: {}", result));

        var t = connectionPool.create()
                .flatMapMany(connection -> {
//                                connection -> connection.createStatement(selectStmt).execute();
                    var stmt = connection.createStatement(insertStmt)
                            .bind(0, "John")
                            .bind(1, "Doe")
                            .bind(2, "John")
                            .bind(3, "Doe")
                            .returnGeneratedValues("last-insert-id");
                            return stmt.execute();
                });

//        var t = connectionPool.create()
//                .flatMapMany(connection ->
//                        connection.createStatement(dropStmt)
//                                .returnGeneratedValues("last-insert-id")
//                                .execute()
//                );


        var t2 = t.flatMap(result -> result.flatMap(segment -> {
            var sqlResult = new SqlResult();
            if (segment instanceof Result.RowSegment rowSegment) {
                var row = rowSegment.row();
                var metadata = row.getMetadata();
                if (!metadata.getColumnMetadatas().isEmpty()) {
                    if (metadata.contains("last-insert-id")) {
                        var generated = row.get("last-insert-id", Integer.class);
                        sqlResult.setGeneratedId(Long.parseLong(generated.toString()));
                    } else {
                        Map<String, Object> data = new HashMap<>();
                        metadata.getColumnMetadatas().forEach(column -> {
                            data.put(column.getName(), row.get(column.getName()));
                        });
                        sqlResult.setData(data);
                    }
                }
            }

            if (segment instanceof Result.UpdateCount) {
                var updateCount = ((Result.UpdateCount) segment).value();
                sqlResult.setUpdatedRow(updateCount);
            }

            return Mono.just(sqlResult);

        }));

        t2.subscribe(System.out::println);

        //.subscribe(sqlResult -> log.info("{}", sqlResult));


//        var l = t.flatMap(result -> result.getRowsUpdated());
//        var r = t.flatMap(result -> result.map((row, rowMetadata) -> row.get("test-id", Long.class)));
//
//        var s = Flux.combineLatest(l, r, (v1, v2) -> v1 + v2).subscribe(aLong -> log.info("final {}", aLong));

//                .doOnNext(result -> log.info("class {}", result.getRowsUpdated()))
//                .flatMap(result -> {
//                    //log.info("{} row updated", ((MySqlResult) result).getRowsUpdated().flatMap(aLong -> ));
//                    return ((MySqlResult)result).map((row, rowMetadata) -> row);
//                })
////                .flatMap(result -> result.getRowsUpdated())
//                .subscribe(row -> {
//                    var name = row.getMetadata().getColumnMetadatas().getFirst().getName();
//                    log.info("{} -> {}", name, row.get(name));
//                });

        latch.await(5000000, TimeUnit.MILLISECONDS);
    }

}


class SqlResult {
    Long generatedId;
    Long updatedRow;
    Map<String, Object> data;

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public SqlResult() {
    }

    public SqlResult(Long generatedId) {
        this.generatedId = generatedId;
    }

    public SqlResult(Long generatedId, Long updatedRow) {
        this.generatedId = generatedId;
        this.updatedRow = updatedRow;
    }

    public Long getGeneratedId() {
        return generatedId;
    }

    public Long getUpdatedRow() {
        return updatedRow;
    }

    public void setGeneratedId(Long generatedId) {
        this.generatedId = generatedId;
    }

    public void setUpdatedRow(Long updatedRow) {
        this.updatedRow = updatedRow;
    }

    @Override
    public String toString() {
        return "SqlResult{" +
                "generatedId=" + generatedId +
                ", updatedRow=" + updatedRow +
                ", data=" + data +
                '}';
    }
}
