package me.asu.pg_eventbus.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import me.asu.pg_eventbus.core.PgEventBusProperties;

@SpringBootApplication(
        scanBasePackages = "me.asu.pg_eventbus",
        exclude = {
                DataSourceAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class
        }
)
@EnableConfigurationProperties(PgEventBusProperties.class)
public class PgEventBusGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PgEventBusGatewayApplication.class, args);
    }
}
