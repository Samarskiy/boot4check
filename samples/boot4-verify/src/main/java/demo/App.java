package demo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    // J005: Jackson 2 serializer attached with the Jackson 2 annotation
    public static class Money {
        public long cents = 1250;
    }

    public static class MoneySerializer extends com.fasterxml.jackson.databind.JsonSerializer<Money> {
        @Override
        public void serialize(Money value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString("12.50 EUR");
        }
    }

    public static class Invoice {
        @JsonSerialize(using = MoneySerializer.class)
        public Money total = new Money();
        public Provider provider = Provider.OFFICE_365;
        public String zeta = "z";
        public String alpha = "a";
    }

    // J003
    public enum Provider {
        OFFICE_365("Office 365");
        private final String label;
        Provider(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    // J004
    public static class Request {
        public boolean enabled;
        public int count;
    }

    // J001: a Jackson 2 ObjectMapper bean with a visible setting
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Bean
    CommandLineRunner verify(JsonMapper jsonMapper, ObjectMapper jackson2, JdbcTemplate jdbc) {
        return args -> {
            System.out.println("RESULT jackson3 (auto-configured JsonMapper): " + jsonMapper.writeValueAsString(new Invoice()));
            System.out.println("RESULT jackson2 (plain new ObjectMapper):    " + new ObjectMapper().writeValueAsString(new Invoice()));
            System.out.println("RESULT J001 auto-configured mapper indents output: "
                    + jsonMapper.writeValueAsString(new Invoice()).contains("\n"));
            try {
                Request parsed = jsonMapper.readValue("{\"enabled\":null,\"count\":null}", Request.class);
                System.out.println("RESULT J004 jackson3 accepted null primitives: enabled=" + parsed.enabled);
            } catch (Exception e) {
                System.out.println("RESULT J004 jackson3 rejected null primitive: " + e.getClass().getSimpleName());
            }
            Request old = new ObjectMapper().readValue("{\"enabled\":null,\"count\":null}", Request.class);
            System.out.println("RESULT J004 jackson2 accepted null primitives: enabled=" + old.enabled);
            Integer tables = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where lower(table_name) in ('verify_marker','flyway_schema_history')",
                    Integer.class);
            System.out.println("RESULT B002 flyway tables present (0 = migrations did not run): " + tables);
        };
    }
}
