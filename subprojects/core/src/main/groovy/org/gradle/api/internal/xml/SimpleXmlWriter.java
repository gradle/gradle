/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedList;

/**
 * <p>A basic XML writer. Encodes characters and CDATA. Provides only basic state validation.</p>
 *
 * <p>This class also is-a Writer, and any characters written to this writer will be encoded as appropriate.</p>
 *
 * by Szczepan Faber, created at: 12/3/12
 */
public class SimpleXmlWriter extends Writer {
    private enum Context {
        Character, CData, StartTag
    }

    private final Writer output;
    private final LinkedList<String> elements = new LinkedList<String>();
    private Context context = Context.Character;
    private int squareBrackets;

    public SimpleXmlWriter(OutputStream output) throws IOException {
        this.output = new OutputStreamWriter(output, "UTF-8");
        writeXmlDeclaration("UTF-8", "1.0");
    }

    private void writeXmlDeclaration(String encoding, String ver) throws IOException {
        writeRaw("<?xml version=\"");
        writeRaw(ver);
        writeRaw("\" encoding=\"");
        writeRaw(encoding);
        writeRaw("\"?>");
    }

    @Override
    public void write(char[] chars, int offset, int length) throws IOException {
        writeCharacters(chars, offset, length);
    }

    @Override
    public void flush() throws IOException {
        output.flush();
    }

    @Override
    public void close() throws IOException {
        // Does nothing
    }

    public void writeCharacters(char[] characters) throws IOException {
        writeCharacters(characters, 0, characters.length);
    }

    public void writeCharacters(char[] characters, int start, int count) throws IOException {
        maybeFinishElement();
        if (context == Context.Character) {
            writeXmlEncoded(characters, start, count);
        } else {
            writeCDATA(characters, start, count);
        }
    }

    public void writeCharacters(CharSequence characters) throws IOException {
        maybeFinishElement();
        if (context == Context.Character) {
            writeXmlEncoded(characters);
        } else {
            writeCDATA(characters);
        }
    }

    private void maybeFinishElement() throws IOException {
        if (context == Context.StartTag) {
            writeRaw(">");
            context = Context.Character;
        }
    }

    public SimpleXmlWriter writeStartElement(String name) throws IOException {
        if (!isValidXmlName(name)) {
            throw new IllegalArgumentException(String.format("Invalid element name: '%s'", name));
        }
        if (context == Context.CData) {
            throw new IllegalStateException("Cannot start element, as current CDATA node has not been closed.");
        }
        maybeFinishElement();
        context = Context.StartTag;
        elements.add(name);
        writeRaw("<");
        writeRaw(name);
        return this;
    }

    public void writeEndElement() throws IOException {
        if (elements.isEmpty()) {
            throw new IllegalStateException("Cannot end element, as there are no started elements.");
        }
        if (context == Context.CData) {
            throw new IllegalStateException("Cannot end element, as current CDATA node has not been closed.");
        }
        if (context == Context.StartTag) {
            writeRaw("/>");
            elements.removeLast();
        } else {
            writeRaw("</");
            writeRaw(elements.removeLast());
            writeRaw(">");
        }
        context = Context.Character;
        if (elements.isEmpty()) {
            output.flush();
        }
    }

    private void writeCDATA(char[] cdata, int offset, int count) throws IOException {
        int end = offset + count;
        for (int i = offset; i < end; i++) {
            writeCDATA(cdata[i]);
        }
    }

    private void writeCDATA(CharSequence cdata) throws IOException {
        int len = cdata.length();
        for (int i = 0; i < len; i++) {
            writeCDATA(cdata.charAt(i));
        }
    }

    private void writeCDATA(char c) throws IOException {
        if (needsCDATAEscaping(c)) {
            writeRaw("]]><![CDATA[>");
        } else {
            writeRaw(c);
        }
    }

    private boolean needsCDATAEscaping(char c) {
        switch (c) {
            case ']':
                squareBrackets++;
                return false;
            case '>':
                if (squareBrackets >= 2) {
                    squareBrackets = 0;
                    return true;
                }
                return false;
            default:
                squareBrackets = 0;
                return false;
        }
    }

    public void writeStartCDATA() throws IOException {
        if (context == Context.CData) {
            throw new IllegalStateException("Cannot start CDATA node, as current CDATA node has not been closed.");
        }
        maybeFinishElement();
        writeRaw("<![CDATA[");
        context = Context.CData;
        squareBrackets = 0;
    }

    public void writeEndCDATA() throws IOException {
        if (context != Context.CData) {
            throw new IllegalStateException("Cannot end CDATA node, as not currently in a CDATA node.");
        }
        writeRaw("]]>");
        context = Context.Character;
    }

    public SimpleXmlWriter attribute(String name, String value) throws IOException {
        if (!isValidXmlName(name)) {
            throw new IllegalArgumentException(String.format("Invalid attribute name: '%s'", name));
        }
        if (context != Context.StartTag) {
            throw new IllegalStateException("Cannot write attribute [" + name + ":" + value + "]. You should write start element first.");
        }

        writeRaw(" ");
        writeRaw(name);
        writeRaw("=\"");
        writeXmlAttributeEncoded(value);
        writeRaw("\"");
        return this;
    }

    private static boolean isValidXmlName(String name) {
        int length = name.length();
        if (length == 0) {
            return false;
        }
        char ch = name.charAt(0);
        if (!isValidNameStartChar(ch)) {
            return false;
        }
        for (int i = 1; i < length; i++) {
            ch = name.charAt(i);
            if (!isValidNameChar(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidNameChar(char ch) {
        if (isValidNameStartChar(ch)) {
            return true;
        }
        if (ch >= '0' && ch <= '9') {
            return true;
        }
        if (ch == '-' || ch == '.' || ch == '\u00b7') {
            return true;
        }
        if (ch >= '\u0300' && ch <= '\u036f') {
            return true;
        }
        if (ch >= '\u203f' && ch <= '\u2040') {
            return true;
        }
        return false;
    }

    private static boolean isValidNameStartChar(char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return true;
        }
        if (ch >= 'a' && ch <= 'z') {
            return true;
        }
        if (ch == ':' || ch == '_') {
            return true;
        }
        if (ch >= '\u00c0' && ch <= '\u00d6') {
            return true;
        }
        if (ch >= '\u00d8' && ch <= '\u00f6') {
            return true;
        }
        if (ch >= '\u00f8' && ch <= '\u02ff') {
            return true;
        }
        if (ch >= '\u0370' && ch <= '\u037d') {
            return true;
        }
        if (ch >= '\u037f' && ch <= '\u1fff') {
            return true;
        }
        if (ch >= '\u200c' && ch <= '\u200d') {
            return true;
        }
        if (ch >= '\u2070' && ch <= '\u218f') {
            return true;
        }
        if (ch >= '\u2c00' && ch <= '\u2fef') {
            return true;
        }
        if (ch >= '\u3001' && ch <= '\ud7ff') {
            return true;
        }
        if (ch >= '\uf900' && ch <= '\ufdcf') {
            return true;
        }
        if (ch >= '\ufdf0' && ch <= '\ufffd') {
            return true;
        }
        return false;
    }

    private void writeRaw(char c) throws IOException {
        output.write(c);
    }

    private void writeRaw(String message) throws IOException {
        output.write(message);
    }

    private void writeXmlEncoded(char[] message, int offset, int count) throws IOException {
        int end = offset + count;
        for (int i = offset; i < end; i++) {
            writeXmlEncoded(message[i]);
        }
    }

    private void writeXmlAttributeEncoded(CharSequence message) throws IOException {
        assert message != null;
        int len = message.length();
        for (int i = 0; i < len; i++) {
            writeXmlAttributeEncoded(message.charAt(i));
        }
    }

    private void writeXmlAttributeEncoded(char ch) throws IOException {
        if (ch == 9) {
            writeRaw("&#9;");
        } else if (ch == 10) {
            writeRaw("&#10;");
        } else if (ch == 13) {
            writeRaw("&#13;");
        } else {
            writeXmlEncoded(ch);
        }
    }

    private void writeXmlEncoded(CharSequence message) throws IOException {
        assert message != null;
        int len = message.length();
        for (int i = 0; i < len; i++) {
            writeXmlEncoded(message.charAt(i));
        }
    }

    private void writeXmlEncoded(char ch) throws IOException {
        if (ch == '<') {
            writeRaw("&lt;");
        } else if (ch == '>') {
            writeRaw("&gt;");
        } else if (ch == '&') {
            writeRaw("&amp;");
        } else if (ch == '"') {
            writeRaw("&quot;");
        } else {
            writeRaw(ch);
        }
    }
}
