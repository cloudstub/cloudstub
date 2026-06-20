package io.cloudstub.sns;

import io.cloudstub.core.spi.StubResponse;
import io.cloudstub.core.spi.XmlElement;
import java.util.UUID;

/**
 * Builds SNS AWS Query (XML) responses from {@link XmlElement} trees. Every response carries the
 * SNS namespace and a {@code ResponseMetadata/RequestId}; operations with output wrap their result
 * in an {@code <Action>Result} element, those without (e.g. {@code DeleteTopic}) carry only the
 * metadata. XML escaping is performed by the engine ({@code XmlElement}), so this class never
 * escapes by hand.
 */
final class SnsXml {

    private SnsXml() {}

    private static final String NS = "http://sns.amazonaws.com/doc/2010-03-31/";

    /** A response whose {@code <Action>Result} element wraps the given result children. */
    static StubResponse result(String action, XmlElement... resultChildren) {
        XmlElement resultEl = XmlElement.of(action + "Result");
        for (XmlElement child : resultChildren) {
            resultEl.child(child);
        }
        return StubResponse.xml(response(action).child(resultEl).child(metadata()));
    }

    /** A response that carries only {@code ResponseMetadata} (no result element). */
    static StubResponse empty(String action) {
        return StubResponse.xml(response(action).child(metadata()));
    }

    /** A sender (4xx) error response in the AWS Query {@code ErrorResponse} format. */
    static StubResponse error(String code, String message) {
        XmlElement error =
                XmlElement.of("ErrorResponse")
                        .attr("xmlns", NS)
                        .child(
                                XmlElement.of("Error")
                                        .child("Type", "Sender")
                                        .child("Code", code)
                                        .child("Message", message))
                        .child("RequestId", UUID.randomUUID().toString());
        return StubResponse.of(400, StubResponse.CONTENT_TYPE_XML, error.render());
    }

    private static XmlElement response(String action) {
        return XmlElement.of(action + "Response").attr("xmlns", NS);
    }

    private static XmlElement metadata() {
        return XmlElement.of("ResponseMetadata").child("RequestId", UUID.randomUUID().toString());
    }
}
