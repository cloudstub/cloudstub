package io.cloudstub.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XmlElementTest {

    @Test
    void rendersNestedElementsWithAttributes() {
        XmlElement root =
                XmlElement.of("CreateTopicResponse")
                        .attr("xmlns", "http://sns.amazonaws.com/doc/2010-03-31/")
                        .child(XmlElement.of("CreateTopicResult").child("TopicArn", "arn:topic"));

        assertEquals(
                "<CreateTopicResponse xmlns=\"http://sns.amazonaws.com/doc/2010-03-31/\">"
                        + "<CreateTopicResult><TopicArn>arn:topic</TopicArn></CreateTopicResult>"
                        + "</CreateTopicResponse>",
                root.render());
    }

    @Test
    void escapesTextAndAttributeValues() {
        XmlElement el = XmlElement.of("Message").attr("note", "a\"b<c").text("x & y < z > w");

        assertEquals(
                "<Message note=\"a&quot;b&lt;c\">x &amp; y &lt; z &gt; w</Message>", el.render());
    }

    @Test
    void emptyElementRendersOpenAndCloseTags() {
        assertEquals("<Topics></Topics>", XmlElement.of("Topics").render());
    }

    @Test
    void nullTextAndAttributeValueRenderAsEmpty() {
        XmlElement el = XmlElement.of("Endpoint").attr("kind", null).text(null);
        assertEquals("<Endpoint kind=\"\"></Endpoint>", el.render());
    }

    @Test
    void repeatedChildrenModelQueryProtocolMemberLists() {
        XmlElement topics =
                XmlElement.of("Topics")
                        .child(XmlElement.of("member").child("TopicArn", "arn:a"))
                        .child(XmlElement.of("member").child("TopicArn", "arn:b"));

        assertEquals(
                "<Topics><member><TopicArn>arn:a</TopicArn></member>"
                        + "<member><TopicArn>arn:b</TopicArn></member></Topics>",
                topics.render());
    }
}
