package io.cloudmock.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnTest {

    @Test
    void defaultsWhenNoArgs() {
        Conn conn = Conn.from(new String[0]);
        assertEquals("localhost", conn.host());
        assertEquals(4567, conn.apiPort());
        assertEquals("http://localhost:4567", conn.apiBaseUrl());
    }

    @Test
    void parsesEqualsForm() {
        Conn conn = Conn.from(new String[]{"--host=example", "--api-port=18080", "status"});
        assertEquals("example", conn.host());
        assertEquals(18080, conn.apiPort());
    }

    @Test
    void parsesSpaceSeparatedForm() {
        Conn conn = Conn.from(new String[]{"--api-port", "9999", "sqs", "list-queues"});
        assertEquals(9999, conn.apiPort());
    }

    @Test
    void ignoresFlagsAfterSubcommandTokens() {
        // Connection flags are read wherever they appear, even after the subcommand.
        Conn conn = Conn.from(new String[]{"sqs", "send-message", "--api-port=4599"});
        assertEquals(4599, conn.apiPort());
    }

    @Test
    void invalidPortFallsBackToDefault() {
        Conn conn = Conn.from(new String[]{"--api-port=not-a-number"});
        assertEquals(4567, conn.apiPort());
    }
}
