/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gradlebuild.docs.dsl.docbook;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Converts the main description of a javadoc comment into a stream of tokens.
 */
class BasicJavadocLexer implements JavadocLexer {
    private static final Pattern HTML_ELEMENT = Pattern.compile("(?s)<\\\\?[^<]+?>");
    private static final Pattern ELEMENT_ATTRIBUTE = Pattern.compile("(?s)\\w+(\\s*=\\s*('.*?')|(\".*?\"))?");
    private static final Pattern END_ATTRIBUTE_NAME = Pattern.compile("=|(\\s)|(/>)|>");
    private static final Pattern ATTRIBUTE_SEPARATOR = Pattern.compile("\\s*=\\s*");
    private static final Pattern END_ELEMENT_NAME = Pattern.compile("\\s+|(/>)|>");
    private static final Pattern END_ELEMENT = Pattern.compile("(/>)|>");
    private static final Pattern HTML_ENCODED_CHAR = Pattern.compile("&#\\d+;");
    private static final Pattern HTML_ENTITY = Pattern.compile("&.+?;");
    private static final Pattern TAG = Pattern.compile("(?s)\\{@.+?\\}");
    private static final Pattern END_TAG_NAME = Pattern.compile("(?s)\\s|}");
    private static final Pattern WHITESPACE_WITH_EOL = Pattern.compile("(?s)\\s+");
    private static final String START_HTML_COMMENT = "<!--";
    private static final String END_HTML_COMMENT = "-->";
    private static final Map<String, String> ENTITIES = new HashMap<String, String>();

    static {
        ENTITIES.put("amp", "&");
        ENTITIES.put("lt", "<");
        ENTITIES.put("gt", ">");
        ENTITIES.put("quot", "\"");
        ENTITIES.put("apos", "'");
    }

    private final JavadocScanner scanner;

    BasicJavadocLexer(JavadocScanner scanner) {
        this.scanner = scanner;
    }

    public void pushText(String rawCommentText) {
        scanner.pushText(rawCommentText);
    }

    @Override
    public void visit(TokenVisitor visitor) {
        while (!scanner.isEmpty()) {
            if (scanner.lookingAt(START_HTML_COMMENT)) {
                skipComment();
                continue;
            }
            if (scanner.lookingAt(HTML_ELEMENT)) {
                parseStartElement(visitor);
                continue;
            }
            if (scanner.lookingAt(TAG)) {
                parseJavadocTag(visitor);
                continue;
            }

            StringBuilder text = new StringBuilder();
            while (!scanner.isEmpty()) {
                if (scanner.lookingAt(START_HTML_COMMENT)) {
                    skipComment();
                    continue;
                }
                if (scanner.lookingAt(HTML_ELEMENT)) {
                    break;
                }
                if (scanner.lookingAt(TAG)) {
                    break;
                }
                if (scanner.lookingAt(HTML_ENCODED_CHAR)) {
                    parseHtmlEncodedChar(text);
                } else if (scanner.lookingAt(HTML_ENTITY)) {
                    parseHtmlEntity(text);
                } else {
                    text.append(scanner.getFirst());
                    scanner.next();
                }
            }

            visitor.onText(text.toString());
        }
        visitor.onEnd();
    }

    private void skipComment() {
        scanner.next(4);
        while (!scanner.isEmpty() && !scanner.lookingAt(END_HTML_COMMENT)) {
            scanner.next();
        }
        if (!scanner.isEmpty()) {
            scanner.next(3);
        }
    }

    private void parseHtmlEntity(StringBuilder buffer) {
        scanner.next();
        scanner.mark();
        scanner.find(';');
        String value = ENTITIES.get(scanner.region().toLowerCase(Locale.ROOT));
        buffer.append(value);
        scanner.next();
    }

    private void parseHtmlEncodedChar(StringBuilder buffer) {
        scanner.next(2);
        scanner.mark();
        scanner.find(';');
        String value = new String(new char[]{(char) Integer.parseInt(scanner.region())});
        buffer.append(value);
        scanner.next();
    }

    private void parseJavadocTag(TokenVisitor visitor) {
        // start of tag marker
        scanner.next(2);

        // tag name
        scanner.mark();
        scanner.find(END_TAG_NAME);
        String tagName = scanner.region();
        visitor.onStartJavadocTag(tagName);
        scanner.skip(WHITESPACE_WITH_EOL);

        // value
        if (!scanner.lookingAt('}')) {
            scanner.mark();
            scanner.find('}');
            String value = scanner.region();
            visitor.onText(value);
        }

        // end of tag marker
        if (scanner.lookingAt('}')) {
            visitor.onEndJavadocTag(tagName);
            scanner.next();
        }
    }

    private void parseStartElement(TokenVisitor visitor) {
        // start element marker
        scanner.next();
        boolean isEnd = false;
        if (scanner.lookingAt('/')) {
            isEnd = true;
            scanner.next();
        }

        // element name
        scanner.skip(WHITESPACE_WITH_EOL);
        scanner.mark();
        scanner.find(END_ELEMENT_NAME);
        String elementName = scanner.region().toLowerCase(Locale.ROOT);
        if (isEnd) {
            visitor.onEndHtmlElement(elementName);
        } else {
            visitor.onStartHtmlElement(elementName);
        }

        // attributes
        scanner.skip(WHITESPACE_WITH_EOL);
        while (!scanner.isEmpty() && scanner.lookingAt(ELEMENT_ATTRIBUTE)) {
            // attribute name
            scanner.mark();
            scanner.find(END_ATTRIBUTE_NAME);
            String attrName = scanner.region();

            // separator
            scanner.skip(ATTRIBUTE_SEPARATOR);

            // value
            char quote = scanner.getFirst();
            scanner.next();
            StringBuilder attrValue = new StringBuilder();
            while (!scanner.isEmpty() && !scanner.lookingAt(quote)) {
                if (scanner.lookingAt(HTML_ENCODED_CHAR)) {
                    parseHtmlEncodedChar(attrValue);
                } else if (scanner.lookingAt(HTML_ENTITY)) {
                    parseHtmlEntity(attrValue);
                } else {
                    attrValue.append(scanner.getFirst());
                    scanner.next();
                }
            }
            visitor.onHtmlElementAttribute(attrName, attrValue.toString());
            scanner.next();
            scanner.skip(WHITESPACE_WITH_EOL);
        }

        if (!isEnd) {
            visitor.onStartHtmlElementComplete(elementName);
        }

        // end element marker
        if (scanner.lookingAt('/')) {
            visitor.onEndHtmlElement(elementName);
        }
        scanner.skip(END_ELEMENT);
    }
}
