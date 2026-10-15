package me.asu.pg_eventbus.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(
        exclude = {
                DataSourceAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class
        }
)
@EnableConfigurationProperties(PgEventBusProperties.class)
public class PgEventBusCliApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PgEventBusCliApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }
}
