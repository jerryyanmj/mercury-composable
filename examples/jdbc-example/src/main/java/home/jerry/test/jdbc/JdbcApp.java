package home.jerry.test.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SpringBootApplication
public class JdbcApp implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(JdbcApp.class);

    public static void main(String args[]) {
        SpringApplication.run(JdbcApp.class, args);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... strings) throws Exception {

        log.info("Creating tables");

        jdbcTemplate.execute("DROP TABLE IF EXISTS `customers` ;");
        jdbcTemplate.execute("CREATE TABLE `customers`(" +
                "id SERIAL, first_name VARCHAR(255), last_name VARCHAR(255))");

        // Split up the array of whole names into an array of first/last names
        List<Object[]> splitUpNames = Arrays.asList("John Woo", "Jeff Dean", "Josh Bloch", "Josh Long").stream()
                .map(name -> name.split(" "))
                .collect(Collectors.toList());

        // Use a Java 8 stream to print out each tuple of the list
        splitUpNames.forEach(name -> log.info(String.format("Inserting customer record for %s %s", name[0], name[1])));

        // Uses JdbcTemplate's batchUpdate operation to bulk load data
        int[] result = jdbcTemplate.batchUpdate("INSERT INTO customers(first_name, last_name) VALUES (?,?)", splitUpNames);
        log.info("Inserted " + result.length + " records");
        log.info("Inserted " + Arrays.toString(result));

        log.info("Querying for customer records where first_name = 'Josh':");
        jdbcTemplate.query(
                        "SELECT id, first_name, last_name FROM customers WHERE first_name = ?",
                        (rs, rowNum) -> new Customer(rs.getLong("id"), rs.getString("first_name"), rs.getString("last_name")), "Josh")
                .forEach(customer -> log.info(customer.toString()));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO customers(first_name, last_name) VALUES (?,?), (?, ?)", PreparedStatement.RETURN_GENERATED_KEYS);
            ps.setString(1, "Jerry");
            ps.setString(2, "Yan");
            ps.setString(3, "Chloe");
            ps.setString(4, "Yan");
            return ps;
        }, keyHolder);
        log.info("Key generated {}", Objects.requireNonNull(keyHolder.getKeyList()));
    }
}

