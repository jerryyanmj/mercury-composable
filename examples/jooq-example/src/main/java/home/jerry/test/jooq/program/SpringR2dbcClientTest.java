package home.jerry.test.jooq.program;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static home.jerry.test.jooq.tables.Service.SERVICE;

public class SpringR2dbcClientTest {

    private static final Logger log = LoggerFactory.getLogger(SpringR2dbcClientTest.class);

    public static void main(String[] args) {
        try {
            ConnectionFactory connectionFactory = ConnectionFactories.get(
                    ConnectionFactoryOptions
                            .parse("r2dbc:mysql://localhost:3306/Token")
                            .mutate()
                            .option(ConnectionFactoryOptions.USER, "root")
                            .option(ConnectionFactoryOptions.PASSWORD, "password")
                            .build()
            );

            DSLContext ctx = DSL.using(connectionFactory);
            var latch = new CountDownLatch(1);

            DatabaseClient client = DatabaseClient.create(connectionFactory);

            var insertWithReturnSqlStmt = DSL.using(SQLDialect.MYSQL).insertInto(SERVICE)
                    .columns(SERVICE.NAME, SERVICE.CREATEDATUTC, SERVICE.UPDATEDATUTC, SERVICE.ISARCHIVED)
                    .values("TEST1", LocalDateTime.now(), LocalDateTime.now(), (byte) 0)
                    .values("TEST2", LocalDateTime.now(), LocalDateTime.now(), (byte) 0);
            System.out.println("============================" + insertWithReturnSqlStmt.getSQL());
            System.out.println("============================" + insertWithReturnSqlStmt.getParams());

            client.sql("select * from SERVICE")
                            .map(r -> r.get("id", int.class))
                                    .all().doOnNext(System.out::println).subscribe();


            var t = client.sql(insertWithReturnSqlStmt.getSQL());
            t = t.filter((s, next) -> next.execute(s.returnGeneratedValues("id")));
            var params = insertWithReturnSqlStmt.getBindValues();
            for (int i = 0; i < params.size(); i++) {
                t = t.bind(i, params.get(i));
            }
            t.map(r -> r.get("id", int.class))
                    .all().doOnNext(System.out::println).subscribe();


            latch.await(500, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}

