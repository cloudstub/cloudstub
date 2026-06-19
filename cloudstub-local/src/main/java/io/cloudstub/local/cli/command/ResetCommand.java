package io.cloudstub.local.cli.command;

import io.cloudstub.local.cli.CloudStubCli;
import io.cloudstub.local.cli.http.ApiClient;
import io.cloudstub.local.cli.http.CloudStubUnavailableException;
import io.cloudstub.local.cli.util.Printer;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "reset", description = "Clear all state, or a single service with --service")
public class ResetCommand implements Callable<Integer> {

    @ParentCommand CloudStubCli root;

    @Option(
            names = {"--service", "-s"},
            description = "Service ID to reset (e.g. sqs, s3)")
    String service;

    @Override
    public Integer call() {
        ApiClient api = new ApiClient(root.apiBaseUrl());
        Map<String, String> query =
                service != null && !service.isBlank() ? Map.of("service", service) : Map.of();
        try {
            ApiClient.Result res = api.call("POST", "/api/reset", query);
            if (!res.isSuccess()) {
                Printer.result(res);
                return 1;
            }
            Printer.json(res.body());
            return 0;
        } catch (CloudStubUnavailableException e) {
            Printer.unavailable(root.apiBaseUrl());
            return 1;
        }
    }
}
