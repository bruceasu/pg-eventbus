package me.asu.pg_eventbus.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PgEventBusCoreServicesTest {

    @Test
    void propertiesRecordCarriesTheExpectedBatchSize() {
        PgEventBusProperties properties = new PgEventBusProperties(true, "consumer-a", 64, 30, 3, null);

        assertEquals(64, properties.batchSize());
        assertEquals("bus", properties.schema());
    }
}
