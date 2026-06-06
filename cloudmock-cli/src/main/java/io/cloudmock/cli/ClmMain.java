package io.cloudmock.cli;

import com.fasterxml.jackson.databind.JsonNode;
import io.cloudmock.cli.command.ResetCommand;
import io.cloudmock.cli.command.StatusCommand;
import io.cloudmock.cli.http.ApiClient;
import io.cloudmock.cli.http.CloudMockUnavailableException;
import io.cloudmock.cli.util.Printer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.ScopeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Entry point for the CloudMock CLI ({@code clm} / {@code cloudmock}).
 *
 * <p>The CLI is a thin HTTP client: it has no compile-time knowledge of any service module.
 * Global commands ({@code status}, {@code reset}) are built in. Service commands
 * ({@code clm sqs send-message}, …) are discovered at runtime from the running instance's
 * {@code /api/status} endpoint — every module route that advertises a {@code service} and
 * {@code command} becomes a {@code clm <service> <command>} subcommand with options derived
 * from the route's declared parameters. Adding a new module therefore adds new CLI commands
 * with no change to this code.
 */
@Command(
    name = "clm",
    mixinStandardHelpOptions = true,
    version = "cloudmock-cli 0.1.0",
    description = "CLI for a running CloudMock instance (thin client over the REST API)",
    subcommands = {HelpCommand.class, StatusCommand.class, ResetCommand.class}
)
public class ClmMain implements Callable<Integer> {

    private static final Set<String> BUILTIN_COMMANDS = Set.of("status", "reset", "help");

    @Option(names = "--host", scope = ScopeType.INHERIT,
            description = "CloudMock host (env: CLOUDMOCK_HOST, default: localhost)")
    String host = "localhost";

    @Option(names = "--api-port", scope = ScopeType.INHERIT,
            description = "REST API port (env: CLOUDMOCK_API_PORT, default: 4567)")
    int apiPort = 4567;

    public String apiBaseUrl() {
        return "http://" + host + ":" + apiPort;
    }

    /** No subcommand given — show usage. */
    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        Conn conn = Conn.from(args);
        ApiClient api = new ApiClient(conn.apiBaseUrl());

        CommandLine cmd = new CommandLine(new ClmMain());

        boolean discovered = false;
        try {
            addServiceCommands(cmd, api.getStatus());
            discovered = true;
        } catch (CloudMockUnavailableException e) {
            // Server down: keep the built-in commands. status/reset will report this cleanly;
            // a service command that can't be discovered is handled just below.
        }

        if (!discovered && targetsUndiscoveredService(args)) {
            Printer.unavailable(conn.apiBaseUrl());
            System.exit(1);
        }

        cmd.setExecutionStrategy(parseResult -> dispatch(parseResult, api));
        System.exit(cmd.execute(args));
    }

    // -------------------------------------------------------------------------
    // Dynamic command discovery
    // -------------------------------------------------------------------------

    /** Build {@code <service> <command>} subcommands from the routes advertised by /api/status. */
    private static void addServiceCommands(CommandLine root, JsonNode status) {
        Map<String, CommandLine> groups = new LinkedHashMap<>();

        for (JsonNode route : status.path("routes")) {
            if (!route.hasNonNull("service") || !route.hasNonNull("command")) {
                continue; // core route (status/reset/…), not a service command
            }
            String service = route.get("service").asText();
            String command = route.get("command").asText();
            String method = route.path("method").asText("GET");
            String path = route.path("path").asText();
            String description = route.path("description").asText("");

            List<String> paramNames = new ArrayList<>();
            CommandSpec leaf = CommandSpec.wrapWithoutInspection(new RouteCmd(method, path, paramNames));
            leaf.mixinStandardHelpOptions(true);
            leaf.usageMessage().description(description);
            for (JsonNode p : route.path("params")) {
                String name = p.path("name").asText();
                paramNames.add(name);
                leaf.addOption(OptionSpec.builder("--" + name)
                        .required(p.path("required").asBoolean(false))
                        .description(p.path("description").asText(""))
                        .paramLabel("<" + name + ">")
                        .type(String.class)
                        .build());
            }

            CommandLine group = groups.computeIfAbsent(service, s -> {
                CommandSpec spec = CommandSpec.wrapWithoutInspection(new ServiceGroup(s));
                spec.mixinStandardHelpOptions(true);
                spec.usageMessage().description("Commands for the " + s + " service");
                return new CommandLine(spec);
            });
            group.addSubcommand(command, new CommandLine(leaf));
        }

        groups.forEach(root::addSubcommand);
    }

    /** True when the first non-option token names something other than a built-in command. */
    private static boolean targetsUndiscoveredService(String[] args) {
        for (String a : args) {
            if (a.startsWith("-")) {
                continue;
            }
            return !BUILTIN_COMMANDS.contains(a);
        }
        return false; // no subcommand token → help/usage, which works offline
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    private static int dispatch(ParseResult parseResult, ApiClient api) {
        Integer help = CommandLine.executeHelpRequest(parseResult);
        if (help != null) {
            return help;
        }

        ParseResult leaf = parseResult;
        while (leaf.hasSubcommand()) {
            leaf = leaf.subcommand();
        }
        Object target = leaf.commandSpec().userObject();

        if (target instanceof ServiceGroup) {
            leaf.commandSpec().commandLine().usage(System.out);
            return 2; // service named without a command
        }
        if (target instanceof RouteCmd route) {
            return invoke(route, leaf, api);
        }
        // Built-in command (status/reset) or the root with no subcommand.
        return new CommandLine.RunLast().execute(parseResult);
    }

    private static int invoke(RouteCmd route, ParseResult leaf, ApiClient api) {
        Map<String, String> query = new LinkedHashMap<>();
        for (String name : route.params()) {
            OptionSpec opt = leaf.commandSpec().findOption("--" + name);
            Object value = opt == null ? null : opt.getValue();
            if (value != null) {
                query.put(name, value.toString());
            }
        }
        try {
            ApiClient.Result res = api.call(route.method(), route.path(), query);
            Printer.result(res);
            return res.isSuccess() ? 0 : 1;
        } catch (CloudMockUnavailableException e) {
            Printer.unavailable(api.baseUrl());
            return 1;
        }
    }

    // userObject markers attached to dynamically built command specs.
    private record RouteCmd(String method, String path, List<String> params) {}

    private record ServiceGroup(String id) {}
}
