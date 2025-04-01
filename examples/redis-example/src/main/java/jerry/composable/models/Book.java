package jerry.composable.models;

import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;

@RedisHash("Book")
public class Book implements Serializable {

    public enum Gender {
        TECH, FICTION
    }

    private Long id;
    private String name;

    public Book(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Book{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
