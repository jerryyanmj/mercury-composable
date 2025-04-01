package home.jerry.test.jooq.program;

import home.jerry.test.jooq.tables.records.ServiceRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static home.jerry.test.jooq.tables.Service.SERVICE;

// This is a blocking example
public class JdbcTest {
    public static void main(String[] args) {

        String jdbcUrl = "jdbc:mysql://localhost:3306/Token";
        String jdbcUsername = "root";
        String jdbcPassword = "password";

        // Establish JDBC connection
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword)) {
            // Get Jooq context
            DSLContext dslContext = DSL.using(connection, SQLDialect.MYSQL);

            // fetch records as result and operate with record type
            Result<Record> recordResult = dslContext.select().from(SERVICE).fetch();
            recordResult.forEach(record -> {
                Integer id = record.getValue(SERVICE.ID);
                String name = record.getValue(SERVICE.NAME);
                System.out.println("fetch Record     id: " + id + " , username: " + name);
            });

            // convert record type to table record type, working with table record class
            Result<ServiceRecord> serviceRecordResult = recordResult.into(SERVICE);
            serviceRecordResult.forEach(record -> {
                Integer id = record.getId();
                String name = record.getName();
                System.out.println("into ServiceRecord   id: " + id + " , username: " + name);
            });

            List<ServiceRecord> fetchIntoClassResultList = dslContext.select().from(SERVICE).fetchInto(ServiceRecord.class);
            Result<ServiceRecord> fetchIntoTableResultList = dslContext.select().from(SERVICE).fetchInto(SERVICE);

            System.out.println("fetchIntoClassResultList: \n" + fetchIntoClassResultList);
            System.out.println("fetchIntoTableResultList: \n" + fetchIntoTableResultList);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}


