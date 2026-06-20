package io.cloudstub.core.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A builder for an XML element tree, used to assemble AWS Query (XML) response bodies without
 * hand-concatenating strings. The engine performs all XML escaping of text and attribute values, so
 * a handler describes the structure and never escapes by hand.
 *
 * <p>Pass the root element to {@link StubResponse#xml(XmlElement)} to produce a response. Elements
 * render as {@code <name attr="v">...</name>}; an element with neither text nor children renders as
 * {@code <name></name>} (not self-closing). Text and child elements are independent; if both are
 * set, the text is rendered before the children. A null text or attribute value renders as empty.
 *
 * <p>JDK-only and exposes no networking or serialisation type.
 */
public final class XmlElement {

    private final String name;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<XmlElement> children = new ArrayList<>();
    private String text;

    private XmlElement(String name) {
        this.name = name;
    }

    /** A new element with the given tag name. */
    public static XmlElement of(String name) {
        return new XmlElement(name);
    }

    /** Adds an attribute. Its value is XML-escaped on render. */
    public XmlElement attr(String name, String value) {
        attributes.put(name, value);
        return this;
    }

    /** Sets the element's text content. It is XML-escaped on render. */
    public XmlElement text(String text) {
        this.text = text;
        return this;
    }

    /** Appends a child element. */
    public XmlElement child(XmlElement child) {
        children.add(child);
        return this;
    }

    /** Appends a child element {@code <name>text</name>}. */
    public XmlElement child(String name, String text) {
        return child(of(name).text(text));
    }

    /**
     * Serialises this element and its subtree to an XML string, escaping all text and attributes.
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        render(sb);
        return sb.toString();
    }

    private void render(StringBuilder sb) {
        sb.append('<').append(name);
        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            sb.append(' ')
                    .append(attr.getKey())
                    .append("=\"")
                    .append(escapeAttribute(attr.getValue()))
                    .append('"');
        }
        sb.append('>');
        if (text != null) {
            sb.append(escapeText(text));
        }
        for (XmlElement child : children) {
            child.render(sb);
        }
        sb.append("</").append(name).append('>');
    }

    private static String escapeText(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttribute(String s) {
        return escapeText(s).replace("\"", "&quot;");
    }
}
