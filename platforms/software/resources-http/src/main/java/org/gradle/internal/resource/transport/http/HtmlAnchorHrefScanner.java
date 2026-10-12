/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.resource.transport.http;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Collects the {@code href} of every {@code <a>} start tag in an HTML document, in document order.
 *
 * <p>Directory listings are produced by whatever server happens to host the repository, so the
 * markup cannot be assumed to be well formed: tags are frequently left unclosed and attribute
 * values unquoted. Building a document tree would mean taking a position on how to repair all of
 * that; since only the anchor hrefs are of interest, this scans the raw text for start tags
 * instead, which is indifferent to how the surrounding markup nests.
 *
 * <p>The scan follows the HTML tokenizer closely enough for that purpose: it skips comments,
 * doctypes, CDATA sections and the content of raw text elements such as {@code <script>}, it
 * accepts single-quoted and unquoted attribute values, and it resolves the character references
 * that can appear in a URL.
 */
@NullMarked
class HtmlAnchorHrefScanner {

    /**
     * Elements whose content is text rather than markup, so an {@code <a>} inside one of them is
     * not a link. See <a href="https://html.spec.whatwg.org/multipage/parsing.html#parsing-html-fragments">the HTML specification</a>.
     */
    private static final Set<String> RAW_TEXT_ELEMENTS = new HashSet<>(Arrays.asList(
        "script", "style", "textarea", "title", "xmp", "iframe", "noembed", "noframes"
    ));

    List<String> scan(String html) {
        List<String> hrefs = new ArrayList<>();
        // Scanning only ever moves forwards, so once a raw text element is known to have no end
        // tag ahead of the current position it can have none ahead of any later position either.
        // Remembering that keeps a document full of unclosed tags from being rescanned each time.
        Set<String> unterminated = new HashSet<>();
        int position = 0;
        while (position < html.length()) {
            int tagStart = html.indexOf('<', position);
            if (tagStart < 0 || tagStart == html.length() - 1) {
                break;
            }
            char afterAngleBracket = html.charAt(tagStart + 1);
            if (afterAngleBracket == '!') {
                position = skipMarkupDeclaration(html, tagStart + 2);
            } else if (afterAngleBracket == '?' || afterAngleBracket == '/') {
                position = skipPast(html, tagStart + 2, ">");
            } else if (isAsciiLetter(afterAngleBracket)) {
                position = readStartTag(html, tagStart, hrefs, unterminated);
            } else {
                // A '<' that starts no tag is just text
                position = tagStart + 1;
            }
        }
        return hrefs;
    }

    /**
     * Reads the start tag beginning at {@code tagStart}, adding its href if it is an anchor, and
     * returns the position to carry on scanning from. For a raw text element that is the end of
     * its content rather than the end of the tag, so that its text is not scanned for tags.
     */
    private static int readStartTag(String html, int tagStart, List<String> hrefs, Set<String> unterminated) {
        int position = tagStart + 1;
        int nameStart = position;
        while (position < html.length() && isTagNameCharacter(html.charAt(position))) {
            position++;
        }
        String tagName = html.substring(nameStart, position).toLowerCase(Locale.ROOT);
        boolean anchor = tagName.equals("a");

        String href = null;
        while (position < html.length()) {
            position = skipWhitespace(html, position);
            if (position == html.length()) {
                break;
            }
            char next = html.charAt(position);
            if (next == '>') {
                position++;
                break;
            }
            if (next == '/') {
                // Either a self-closing tag or a stray slash between attributes
                position++;
                continue;
            }

            int attributeNameStart = position;
            while (position < html.length() && !isAttributeNameEnd(html.charAt(position))) {
                position++;
            }
            String attributeName = html.substring(attributeNameStart, position).toLowerCase(Locale.ROOT);

            // Duplicate attributes are dropped by the HTML parser, so only the first href counts
            boolean anchorHref = anchor && href == null && attributeName.equals("href");
            int afterName = skipWhitespace(html, position);
            if (afterName == html.length() || html.charAt(afterName) != '=') {
                // A valueless attribute has the empty string as its value. Leave the position
                // alone, so that the character stopping the name starts the next attribute.
                if (anchorHref) {
                    href = "";
                }
                continue;
            }
            position = skipWhitespace(html, afterName + 1);
            int valueStart;
            int valueEnd;
            char quote = position < html.length() ? html.charAt(position) : '>';
            if (quote == '"' || quote == '\'') {
                valueStart = position + 1;
                valueEnd = html.indexOf(quote, valueStart);
                if (valueEnd < 0) {
                    valueEnd = html.length();
                    position = valueEnd;
                } else {
                    position = valueEnd + 1;
                }
            } else {
                valueStart = position;
                while (position < html.length() && !isUnquotedValueEnd(html.charAt(position))) {
                    position++;
                }
                valueEnd = position;
            }

            if (anchorHref) {
                href = resolveCharacterReferences(html.substring(valueStart, valueEnd));
            }
        }

        if (href != null) {
            hrefs.add(href);
        }
        if (!RAW_TEXT_ELEMENTS.contains(tagName) || unterminated.contains(tagName)) {
            return position;
        }
        return skipRawText(html, position, tagName, unterminated);
    }

    /**
     * Skips a comment, doctype, CDATA section or any other {@code <!...>} construct. Anything that
     * is not a comment ends at the first {@code >}, which is what makes a listing wrapped in a
     * CDATA section yield no links, matching how a browser reads it.
     */
    private static int skipMarkupDeclaration(String html, int position) {
        if (html.startsWith("--", position)) {
            return skipPast(html, position + 2, "-->");
        }
        return skipPast(html, position, ">");
    }

    /**
     * Skips the text content of a raw text element, returning the position just past its end tag.
     *
     * <p>If there is no end tag the content is not skipped at all. Treating the rest of the
     * document as text would be the letter of the specification, but for a listing that would
     * silently discard every remaining link; picking the links up is the more useful reading of
     * markup that is already broken.
     */
    private static int skipRawText(String html, int position, String tagName, Set<String> unterminated) {
        int searchFrom = position;
        while (searchFrom < html.length()) {
            int closeTagStart = html.indexOf("</", searchFrom);
            if (closeTagStart < 0) {
                break;
            }
            int nameStart = closeTagStart + 2;
            int nameEnd = nameStart + tagName.length();
            if (html.regionMatches(true, nameStart, tagName, 0, tagName.length())
                && (nameEnd == html.length() || isTagNameEnd(html.charAt(nameEnd)))) {
                return skipPast(html, nameEnd, ">");
            }
            searchFrom = nameStart;
        }
        unterminated.add(tagName);
        return position;
    }

    private static int skipPast(String html, int position, String terminator) {
        int end = html.indexOf(terminator, position);
        return end < 0 ? html.length() : end + terminator.length();
    }

    private static int skipWhitespace(String html, int position) {
        while (position < html.length() && isWhitespace(html.charAt(position))) {
            position++;
        }
        return position;
    }

    /**
     * Replaces the named and numeric character references that can appear in a URL. References
     * that are not recognized are left as they are, which keeps an unescaped {@code &} in a query
     * string intact rather than mangling it.
     */
    private static String resolveCharacterReferences(String value) {
        if (value.indexOf('&') < 0) {
            return value;
        }
        StringBuilder resolved = new StringBuilder(value.length());
        int position = 0;
        while (position < value.length()) {
            int ampersand = value.indexOf('&', position);
            int semicolon = ampersand < 0 ? -1 : value.indexOf(';', ampersand + 1);
            if (semicolon < 0) {
                resolved.append(value, position, value.length());
                break;
            }
            resolved.append(value, position, ampersand);
            String reference = value.substring(ampersand + 1, semicolon);
            String replacement = characterFor(reference);
            resolved.append(replacement != null ? replacement : value.substring(ampersand, semicolon + 1));
            position = semicolon + 1;
        }
        return resolved.toString();
    }

    private static @Nullable String characterFor(String reference) {
        switch (reference) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            case "nbsp":
                return " ";
            default:
                return numericCharacterFor(reference);
        }
    }

    private static @Nullable String numericCharacterFor(String reference) {
        if (reference.length() < 2 || reference.charAt(0) != '#') {
            return null;
        }
        boolean hex = reference.charAt(1) == 'x' || reference.charAt(1) == 'X';
        String digits = reference.substring(hex ? 2 : 1);
        if (digits.isEmpty()) {
            return null;
        }
        try {
            int codePoint = Integer.parseInt(digits, hex ? 16 : 10);
            if (!Character.isValidCodePoint(codePoint)) {
                return null;
            }
            return new String(Character.toChars(codePoint));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isTagNameCharacter(char c) {
        return !isTagNameEnd(c);
    }

    private static boolean isTagNameEnd(char c) {
        return isWhitespace(c) || c == '>' || c == '/';
    }

    private static boolean isAttributeNameEnd(char c) {
        return isWhitespace(c) || c == '=' || c == '>';
    }

    private static boolean isUnquotedValueEnd(char c) {
        return isWhitespace(c) || c == '>';
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }
}
