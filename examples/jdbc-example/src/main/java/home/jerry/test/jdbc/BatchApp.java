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
public class BatchApp implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchApp.class);

    public static void main(String args[]) {
        SpringApplication.run(BatchApp.class, args);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... strings) throws Exception {

        log.info("Creating tables");

        jdbcTemplate.execute("DROP TABLE IF EXISTS `customers` ;");
        jdbcTemplate.execute("CREATE TABLE `customers`(" +
                "id SERIAL, first_name VARCHAR(255), last_name VARCHAR(255))");


        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO customers(first_name, last_name) VALUES (?,?)", PreparedStatement.RETURN_GENERATED_KEYS);
            ps.setString(1, "Jerry");
            ps.setString(2, "Yan");
            return ps;
        }, keyHolder);
        log.info("Key generated {}", Objects.requireNonNull(keyHolder.getKeyList()));
    }
}

