package me.asu.pg_eventbus.core;

import me.asu.pg_eventbus.contract.EventDto;

@FunctionalInterface
public interface PgEventBusEventHandler {

    void handle(EventDto event) throws Exception;
}