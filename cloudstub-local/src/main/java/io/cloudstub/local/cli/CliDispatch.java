package io.cloudstub.local.cli;

import java.util.Set;

/**
 * Decides whether a {@code cloudstub-local} invocation runs the server or the CLI.
 *
 * <p>The jar is dual-mode: with no positional token (or an explicit {@code serve}) it boots the
 * mock + REST + console; with a command token ({@code status}, {@code reset}, {@code sqs
 * send-message}, …) it runs the CLI against a running instance without booting the server.
 *
 * <p>Serve options may be written in space form ({@code --port 4566}) as well as {@code =} form, so
 * a flag's space-separated value must not be mistaken for a command token. The first token that is
 * neither a flag nor a flag value is the command; if there is none, or it is {@code serve}, the
 * invocation is a server start.
 */
public final class CliDispatch {

    /** Options that consume the following token as their value when written in space form. */
    private static final Set<String> VALUE_FLAGS =
            Set.of(
                    "--port",
                    "--api-port",
                    "--max-history",
                    "--store-dir",
                    "--modules-dir",
                    "--services",
                    "--config",
                    "--module-version",
                    "--maven-base-url",
                    "--host");

    /** Flags the server launcher does not understand, so they belong to the CLI. */
    private static final Set<String> HELP_VERSION_FLAGS =
            Set.of("--help", "-h", "--version", "-V");

    private CliDispatch() {}

    /** True when the arguments name a CLI command rather than a server start. */
    public static boolean isCliInvocation(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-")) {
                if (HELP_VERSION_FLAGS.contains(a)) {
                    return true;
                }
                if (VALUE_FLAGS.contains(a)) {
                    i++; // skip the value of a space-form flag
                }
                continue;
            }
            return !a.equals("serve"); // first command token
        }
        return false; // only server flags (or empty) → server start
    }

    /**
     * Returns the arguments with a leading {@code serve} token removed, leaving server flags
     * intact. Arguments that do not begin with {@code serve} are returned unchanged.
     */
    public static String[] stripServe(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-")) {
                if (VALUE_FLAGS.contains(a)) {
                    i++;
                }
                continue;
            }
            if (a.equals("serve")) {
                String[] trimmed = new String[args.length - 1];
                System.arraycopy(args, 0, trimmed, 0, i);
                System.arraycopy(args, i + 1, trimmed, i, args.length - i - 1);
                return trimmed;
            }
            return args; // first command token is not `serve`
        }
        return args;
    }
}
