package me.asu.pg_eventbus.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.pg_eventbus.cli.commands.DbInitCommand;
import me.asu.pg_eventbus.cli.commands.EventCommand;
import me.asu.pg_eventbus.cli.commands.MaintenanceCommand;
import me.asu.pg_eventbus.cli.commands.PartitionCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class PgEventBusCliRunner implements ApplicationRunner {

    private final ObjectProvider<DbInitCommand> dbInitCommand;
    private final ObjectProvider<PartitionCommand> partitionCommand;
    private final ObjectProvider<EventCommand> eventCommand;
    private final ObjectProvider<MaintenanceCommand> maintenanceCommand;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public PgEventBusCliRunner(
            ObjectProvider<DbInitCommand> dbInitCommand,
            ObjectProvider<PartitionCommand> partitionCommand,
            ObjectProvider<EventCommand> eventCommand,
            ObjectProvider<MaintenanceCommand> maintenanceCommand
    ) {
        this.dbInitCommand = dbInitCommand;
        this.partitionCommand = partitionCommand;
        this.eventCommand = eventCommand;
        this.maintenanceCommand = maintenanceCommand;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> nonOptionArgs = args.getNonOptionArgs();
        if (nonOptionArgs.isEmpty()) {
            printUsage();
            return;
        }

        switch (nonOptionArgs.get(0)) {
            case "db" -> handleDb(nonOptionArgs);
            case "partition" -> handlePartition(nonOptionArgs);
            case "event" -> handleEvent(nonOptionArgs);
            case "maintenance" -> handleMaintenance(nonOptionArgs);
            default -> printUsage();
        }
    }

    private void handleDb(List<String> args) {
        if (args.size() >= 2 && "init".equals(args.get(1))) {
            String owner = args.size() >= 3 ? args.get(2) : System.getenv("PGEVENTBUS_DB_OWNER");
            if (owner == null || owner.isBlank()) {
                System.out.println("Usage:");
                System.out.println("  db init <owner>");
                return;
            }
            DbInitCommand command = dbInitCommand.getIfAvailable();
            if (command == null) {
                printUsage();
                return;
            }
            command.initialize(owner);
            System.out.println("db init completed");
        } else {
            printUsage();
        }
    }

    private void handlePartition(List<String> args) {
        if (args.size() < 2) {
            printUsage();
            return;
        }
        PartitionCommand command = partitionCommand.getIfAvailable();
        if (command == null) {
            printUsage();
            return;
        }
        switch (args.get(1)) {
            case "ensure-current" -> print(command.ensureCurrent());
            case "ensure-future" -> print(command.ensureFuture());
            case "drop-older-than" -> {
                if (args.size() < 3) {
                    printUsage();
                    return;
                }
                print(command.dropOlderThan(Integer.parseInt(args.get(2))));
            }
            default -> printUsage();
        }
    }

    private void handleEvent(List<String> args) throws Exception {
        if (args.size() < 2) {
            printUsage();
            return;
        }
        EventCommand command = eventCommand.getIfAvailable();
        if (command == null) {
            printUsage();
            return;
        }
        switch (args.get(1)) {
            case "send", "publish" -> {
                if (args.size() < 6) {
                    printUsage();
                    return;
                }
                print(command.publish(args.get(2), args.get(3), args.get(4), args.get(5)));
            }
            case "get", "poll" -> {
                if (args.size() < 4) {
                    printUsage();
                    return;
                }
                Integer limit = args.size() >= 5 ? Integer.valueOf(args.get(4)) : null;
                print(command.poll(args.get(2), args.get(3), limit));
            }
            case "set-cursor", "commit" -> {
                if (args.size() < 5) {
                    printUsage();
                    return;
                }
                print(command.commit(args.get(2), args.get(3), Long.parseLong(args.get(4))));
            }
            case "recover" -> {
                if (args.size() < 4) {
                    printUsage();
                    return;
                }
                print(command.recover(args.get(2), Instant.parse(args.get(3))));
            }
            default -> printUsage();
        }
    }

    private void handleMaintenance(List<String> args) {
        if (args.size() >= 2 && "run".equals(args.get(1))) {
            MaintenanceCommand command = maintenanceCommand.getIfAvailable();
            if (command == null) {
                printUsage();
                return;
            }
            print(command.run());
        } else {
            printUsage();
        }
    }

    private void print(Object value) {
        try {
            System.out.println(objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            System.out.println(String.valueOf(value));
        }
    }

    private void printUsage() {
        System.out.println("Usage:");
        System.out.println("  db init <owner>");
        System.out.println("  partition ensure-current");
        System.out.println("  partition ensure-future");
        System.out.println("  partition drop-older-than <keepMonths>");
        System.out.println("  event send|publish <source> <target> <type> <payloadJson>");
        System.out.println("  event get|poll <consumerName> <instanceId> [limit]");
        System.out.println("  event set-cursor|commit <consumerName> <instanceId> <newLastId>");
        System.out.println("  event recover <consumerName> <isoInstant>");
        System.out.println("  maintenance run");
    }
}
