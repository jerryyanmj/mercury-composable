package jerry.composable.examples;

import jerry.composable.models.Book;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class RedisSpringExample implements ApplicationRunner {

    public static void main(String[] args) {
        new SpringApplicationBuilder(RedisSpringExample.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("Test");

        Book b = new Book(1L, "Java");

        RedisTemplate<Long, Book> rTemplate = redisTemplate(new LettuceConnectionFactory());
        rTemplate.opsForValue().set(b.getId(), b, 1000, TimeUnit.MICROSECONDS);

        Book br = rTemplate.opsForValue().get(b.getId());

        System.out.println(br);
    }

    @Bean
    public RedisTemplate<Long, Book> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<Long, Book> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}


