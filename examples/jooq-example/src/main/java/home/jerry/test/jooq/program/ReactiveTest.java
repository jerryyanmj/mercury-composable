package home.jerry.test.jooq.program;

import dev.miku.r2dbc.mysql.MySqlConnectionConfiguration;
import dev.miku.r2dbc.mysql.MySqlConnectionFactory;
import home.jerry.test.jooq.tables.records.ServiceRecord;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.*;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Param;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.platformlambda.core.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static home.jerry.test.jooq.tables.Service.SERVICE;

public class ReactiveTest {

    private static final Logger log = LoggerFactory.getLogger(ReactiveTest.class);

    public static void main(String[] args) {
        try {
//            ConnectionFactory connectionFactory = ConnectionFactories.get(
//                    ConnectionFactoryOptions
//                            .parse("r2dbc:mysql://localhost:3306/Token")
//                            .mutate()
//                            .option(ConnectionFactoryOptions.USER, "root")
//                            .option(ConnectionFactoryOptions.PASSWORD, "password")
//                            .build()
//            );


            ConnectionFactory connectionFactory = MySqlConnectionFactory.from(MySqlConnectionConfiguration.builder().host("127.0.0.1").database("Token").user("root").password("password").build());

//            ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(connectionFactory)
//                    .maxIdleTime(Duration.ofMillis(1000))
//                    .maxSize(20)
//                    .build();
//
//            ConnectionPool pool = new ConnectionPool(configuration);

            DSLContext ctx = DSL.using(connectionFactory);

//            Flux.range(1, 5).subscribe(System.out::println);
            var latch = new CountDownLatch(1);

            /**
             * test DDL
             */
//            var createTableSql = """
//                        create table Service_2
//                        (
//                            Id           int auto_increment
//                                primary key,
//                            Name         varchar(255) not null,
//                            CreatedAtUtc datetime(6)  not null,
//                            UpdatedAtUtc datetime(6)  null,
//                            IsArchived   tinyint(1)   not null,
//                            constraint IX_Service_Name
//                                unique (Name)
//                        );
//                    """;


            /**
             * Test insert
             */
            var insertSqlStmt = DSL.using(SQLDialect.MYSQL).insertInto(SERVICE)
                    .columns(SERVICE.NAME, SERVICE.CREATEDATUTC, SERVICE.UPDATEDATUTC, SERVICE.ISARCHIVED)
                    .values("TEST1", LocalDateTime.now(), LocalDateTime.now(), (byte) 0)
                    .values("TEST2", LocalDateTime.now(), LocalDateTime.now(), (byte) 0)
                    .values("TEST3", LocalDateTime.now(), LocalDateTime.now(), (byte) 0)
                    .values("TEST4", LocalDateTime.now(), LocalDateTime.now(), (byte) 0);
            System.out.println("============================" + insertSqlStmt.getSQL());
            System.out.println("============================" + insertSqlStmt.getParams());
//            Flux.usingWhen(
//                            connectionFactory.create(),
//                            c -> {
//                                var stmt = c.createStatement(insertSqlStmt.getSQL());
//                                var params = insertSqlStmt.getBindValues();
//                                for (int i = 0; i < params.size(); i++) {
//                                    stmt = stmt.bind(i, params.get(i));
//                                }
//                                return stmt.execute();
//                            },
//                            Connection::close
//                    )
//                    .flatMap(result -> result.getRowsUpdated()).map(aLong -> Map.of("ROWS_UPDATED", (Object) aLong))
//                    .map(stringObjectMap -> new SqlCommandResult(new SqlCommand(insertSqlStmt.getSQL(), insertSqlStmt.getBindValues()), stringObjectMap))
//                    .onErrorResume(throwable -> {
//                        log.error("error", throwable);
//                        return Flux.just(new SqlCommandResult(new SqlCommand(insertSqlStmt.getSQL(), insertSqlStmt.getBindValues()), throwable));
//                    })
//                    .doOnNext(System.out::println).subscribe();



            /**
             * Test insert
             */
            var insertWithReturnSqlStmt = DSL.using(SQLDialect.MYSQL).insertInto(SERVICE)
                    .columns(SERVICE.NAME, SERVICE.CREATEDATUTC, SERVICE.UPDATEDATUTC, SERVICE.ISARCHIVED)
                    .values("TEST1", LocalDateTime.now(), LocalDateTime.now(), (byte) 0)
                    .values("TEST2", LocalDateTime.now(), LocalDateTime.now(), (byte) 0);
//                    .values("TEST3", LocalDateTime.now(), LocalDateTime.now(), (byte) 0)
//                    .values("TEST4", LocalDateTime.now(), LocalDateTime.now(), (byte) 0);
            System.out.println("============================" + insertWithReturnSqlStmt.getSQL());
            System.out.println("============================" + insertWithReturnSqlStmt.getParams());


            String createSql = """
                    CREATE TABLE IF NOT EXISTS persons (
                    id int auto_increment PRIMARY KEY,
                    first_name VARCHAR(255),
                    last_name VARCHAR(255),
                    age INTEGER
                    )
                    """;

            String insertSql = """
                    INSERT INTO persons(first_name, last_name, age)
                    VALUES
                    ('Hello', 'Kitty', 20),
                    ('Hantsy', 'Bai', 40)
                    """;

            var conn = connectionFactory.create();
            var s = Mono.from(conn).flatMap(c -> {
                var st = c.createStatement(insertSql);
                st.bind(1, "0");
            });
            Mono.from(conn)
                    .flatMap(
                            c -> Mono.from(c.createStatement(createSql)
                                    .execute())
                    )
                    .log()
                    .doOnNext(data -> log.info("created: {}", data))
                    .then()
                    .thenMany(
                            Mono.from(conn)
                                    .flatMapMany(
                                            c -> c.createStatement(insertSql)
                                                    .returnGeneratedValues("id")
                                                    .returnGeneratedValues("id")
                                                    .execute()

                                    )
                    )
                    .flatMap(data -> Flux.from(data.map((row, rowMetadata) -> row.get("id"))))
                    .doOnNext(id -> log.info("[BeforeEach]generated id: {}", id))
                    .blockLast(Duration.ofSeconds(5));
//            Flux.usingWhen(
//                            connectionFactory.create(),
//                            c -> {
//                                var stmt = c.createStatement(insertWithReturnSqlStmt.getSQL());
//                                var params = insertWithReturnSqlStmt.getBindValues();
//                                for (int i = 0; i < params.size(); i++) {
//                                    stmt = stmt.bind(i, params.get(i));
//                                }
//                                return stmt.returnGeneratedValues("Id").execute();
//                            },
//                            Connection::close
//                    )
//                    .flatMap(result -> result.getRowsUpdated()).map(aLong -> Map.of("ROWS_UPDATED", (Object) aLong))
//                    .map(stringObjectMap -> new SqlCommandResult(new SqlCommand(insertWithReturnSqlStmt.getSQL(), insertWithReturnSqlStmt.getBindValues()), stringObjectMap))
//                    .onErrorResume(throwable -> {
//                        log.error("error", throwable);
//                        return Flux.just(new SqlCommandResult(new SqlCommand(insertWithReturnSqlStmt.getSQL(), insertWithReturnSqlStmt.getBindValues()), throwable));
//                    })
//                    .doOnNext(System.out::println).subscribe();


            /**
             * Test update
             */
//            var updateSqlStmt = DSL.using(SQLDialect.MYSQL).update(SERVICE).set(SERVICE.NAME, "T_%s".formatted(UUID.randomUUID())).where(SERVICE.ID.greaterThan(30));
//            System.out.println("============================" + updateSqlStmt.getSQL());
//            System.out.println("============================" + updateSqlStmt.getParams());
//            Flux.usingWhen(
//                            connectionFactory.create(),
//                            c -> {
//                                var stmt = c.createStatement(updateSqlStmt.getSQL());
//                                var params = updateSqlStmt.getBindValues();
//                                for (int i = 0; i < params.size(); i++) {
//                                    stmt = stmt.bind(i, params.get(i));
//                                }
//                                return stmt.execute();
//                            },
//                            Connection::close
//                    ).flatMap(result -> result.getRowsUpdated()).map(aLong -> Map.of("ROWS_UPDATED", (Object) aLong))
//                    .map(stringObjectMap -> new SqlCommandResult(new SqlCommand(updateSqlStmt.getSQL(), updateSqlStmt.getBindValues()), stringObjectMap))
//                    .onErrorResume(throwable -> Flux.just(new SqlCommandResult(new SqlCommand(updateSqlStmt.getSQL(), updateSqlStmt.getBindValues()), throwable)))
//                    .doOnNext(System.out::println).subscribe();


            /**
             * Test query
             */
//            var selectSqlStmt = DSL.using(SQLDialect.MYSQL).selectFrom(SERVICE);
//            System.out.println("============================" + selectSqlStmt.getSQL());
//            System.out.println("============================" + selectSqlStmt.getParams());
//
//            Flux.usingWhen(
//                            connectionFactory.create(),
//                            c -> {
//                                var stmt = c.createStatement(selectSqlStmt.getSQL());
//                                var params = selectSqlStmt.getBindValues();
//                                for (int i = 0; i < params.size(); i++) {
//                                    stmt = stmt.bind(i, params.get(i));
//                                }
//                                return stmt.execute();
//                            },
//                            Connection::close
//                    ).flatMap(result -> {
//                                var touched = result.getRowsUpdated();
//                                return result.map((r, m) -> {
//                                    Map<String, Object> t = m.getColumnMetadatas().stream().collect(Collectors.toMap(ReadableMetadata::getName, v -> r.get(v.getName())));
//                                    return t;
//                                });
//                            }
//                    )
//                    .map(stringObjectMap -> new SqlCommandResult(new SqlCommand(selectSqlStmt.getSQL(), selectSqlStmt.getBindValues()), stringObjectMap))
//                    .onErrorResume(throwable -> Flux.just(new SqlCommandResult(new SqlCommand(selectSqlStmt.getSQL(), selectSqlStmt.getBindValues()), throwable)))
//                    .doOnNext(System.out::println).subscribe();







            latch.await(5000, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}

