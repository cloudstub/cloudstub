package io.cloudstub.local.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import io.cloudstub.local.cli.CloudStubCli;
import io.cloudstub.local.cli.http.ApiClient;
import io.cloudstub.local.cli.http.CloudStubUnavailableException;
import io.cloudstub.local.cli.util.Printer;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "status",
        description = "Show running instance info: port, uptime, and loaded modules")
public class StatusCommand implements Callable<Integer> {

    @ParentCommand CloudStubCli root;

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
                    System.out.printf(
                            "  %-16s %d stub(s)%n",
                            module.path("id").asText(), module.path("stubs").size());
                }
            }
            return 0;
        } catch (CloudStubUnavailableException e) {
            Printer.unavailable(root.apiBaseUrl());
            return 1;
        }
    }
}
