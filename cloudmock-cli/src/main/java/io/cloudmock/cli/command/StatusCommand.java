package io.cloudmock.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import io.cloudmock.cli.ClmMain;
import io.cloudmock.cli.http.ApiClient;
import io.cloudmock.cli.http.CloudMockUnavailableException;
import io.cloudmock.cli.util.Printer;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "status", description = "Show running instance info: port, uptime, and loaded modules")
public class StatusCommand implements Callable<Integer> {

    @ParentCommand
    ClmMain root;

    @Override
    public Integer call() {
        ApiClient api = new ApiClient(root.apiBaseUrl());
        try {
            JsonNode status = api.getStatus();
            Printer.kv("mock port", status.path("port").asText());
            Printer.kv("api port", status.path("apiPort").asText());
            Printer.kv("started at", status.path("startedAt").asText());
            Printer.kv("uptime", status.path("uptime").asText());
            System.out.println();
            Printer.header("Modules");
            JsonNode modules = status.path("modules");
            if (modules.isEmpty()) {
                System.out.println("  (none)");
            } else {
                for (JsonNode module : modules) {
                    System.out.printf("  %-16s %d stub(s)%n",
                            module.path("id").asText(), module.path("stubs").size());
                }
            }
            return 0;
        } catch (CloudMockUnavailableException e) {
            Printer.unavailable(root.apiBaseUrl());
            return 1;
        }
    }
}
