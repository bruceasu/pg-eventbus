package me.asu.pg_eventbus.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = PgEventBusGatewayApplication.class, properties = "spring.datasource.url=jdbc:postgresql://localhost:1/wiring_only")
class PgEventBusGatewayApplicationTests {
    @Autowired private GatewayService database;
    @Autowired private org.springframework.context.ApplicationContext context;

    @Test
    void contextLoadsWithDatabaseConfiguration() {
        assertNotNull(database);
        assertNotNull(context.getBean(me.asu.pg_eventbus.gateway.api.EventController.class));
    }
}
