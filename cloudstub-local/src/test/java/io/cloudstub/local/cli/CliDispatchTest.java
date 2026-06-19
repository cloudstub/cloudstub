package io.cloudstub.local.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CliDispatchTest {

    @Test
    void noArgsIsServer() {
        assertFalse(CliDispatch.isCliInvocation(new String[0]));
    }

    @Test
    void explicitServeIsServer() {
        assertFalse(CliDispatch.isCliInvocation(new String[] {"serve"}));
    }

    @Test
    void serverFlagsOnlyIsServer() {
        assertFalse(CliDispatch.isCliInvocation(new String[] {"--port=4566", "--services=sqs,s3"}));
    }

    @Test
    void spaceFormFlagValueIsNotMistakenForCommand() {
        // `--port 4566` must resolve as a server start, not a CLI command named "4566".
        assertFalse(CliDispatch.isCliInvocation(new String[] {"--port", "4566"}));
        assertFalse(
                CliDispatch.isCliInvocation(
                        new String[] {"--services", "sqs", "--store-dir", "/tmp/x"}));
    }

    @Test
    void builtinCommandsAreCli() {
        assertTrue(CliDispatch.isCliInvocation(new String[] {"status"}));
        assertTrue(CliDispatch.isCliInvocation(new String[] {"reset", "--service", "sqs"}));
    }

    @Test
    void serviceCommandIsCli() {
        assertTrue(
                CliDispatch.isCliInvocation(
                        new String[] {"sqs", "send-message", "--queue", "orders"}));
    }

    @Test
    void helpAndVersionFlagsAreCli() {
        // The server launcher has no --help/--version, so they must route to the CLI rather than
        // falling through to a server start.
        assertTrue(CliDispatch.isCliInvocation(new String[] {"--help"}));
        assertTrue(CliDispatch.isCliInvocation(new String[] {"-h"}));
        assertTrue(CliDispatch.isCliInvocation(new String[] {"--version"}));
        assertTrue(CliDispatch.isCliInvocation(new String[] {"-V"}));
    }

    @Test
    void cliConnectionFlagsBeforeCommandStillCli() {
        assertTrue(CliDispatch.isCliInvocation(new String[] {"--api-port", "9001", "status"}));
        assertTrue(CliDispatch.isCliInvocation(new String[] {"--host=example", "status"}));
    }

    @Test
    void stripServeRemovesOnlyTheLeadingServeToken() {
        assertArrayEquals(
                new String[] {"--port=4566"},
                CliDispatch.stripServe(new String[] {"serve", "--port=4566"}));
        assertArrayEquals(
                new String[] {"--port", "4566", "--services=sqs"},
                CliDispatch.stripServe(new String[] {"--port", "4566", "serve", "--services=sqs"}));
    }

    @Test
    void stripServeLeavesServerFlagsUnchangedWhenNoServeToken() {
        String[] args = {"--port=4566", "--services=sqs"};
        assertArrayEquals(args, CliDispatch.stripServe(args));
    }
}
