package io.cloudstub.sns;

import java.util.UUID;

/**
 * Builds SNS AWS Query (XML) response bodies. Every response carries the SNS namespace and a {@code
 * ResponseMetadata/RequestId}; operations with output wrap it in an {@code <Action>Result} element,
 * those without (e.g. {@code DeleteTopic}) carry only the metadata.
 */
final class SnsXml {

    private SnsXml() {}

    private static final String NS = "http://sns.amazonaws.com/doc/2010-03-31/";

    /** A response whose {@code <Action>Result} element wraps {@code resultBody}. */
    static String result(String action, String resultBody) {
        return "<"
                + action
                + "Response xmlns=\""
                + NS
                + "\"><"
                + action
                + "Result>"
                + resultBody
                + "</"
                + action
                + "Result>"
                + metadata()
                + "</"
                + action
                + "Response>";
    }

    /** A response that carries only {@code ResponseMetadata} (no result element). */
    static String empty(String action) {
        return "<"
                + action
                + "Response xmlns=\""
                + NS
                + "\">"
                + metadata()
                + "</"
                + action
                + "Response>";
    }

    /** A sender (4xx) error body in the AWS Query {@code ErrorResponse} format. */
    static String error(String code, String message) {
        return "<ErrorResponse xmlns=\""
                + NS
                + "\"><Error><Type>Sender</Type><Code>"
                + escape(code)
                + "</Code><Message>"
                + escape(message)
                + "</Message></Error>"
                + "<RequestId>"
                + UUID.randomUUID()
                + "</RequestId></ErrorResponse>";
    }

    static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String metadata() {
        return "<ResponseMetadata><RequestId>"
                + UUID.randomUUID()
                + "</RequestId></ResponseMetadata>";
    }
}
