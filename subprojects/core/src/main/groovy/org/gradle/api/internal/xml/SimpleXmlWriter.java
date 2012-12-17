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
 * Basic XML writer. Encodes characters and CDATA. Provides only basic state validation.
 *
 * by Szczepan Faber, created at: 12/3/12
 */
public class SimpleXmlWriter {

    private final Writer output;
    private final LinkedList<String> elements = new LinkedList<String>();
    private boolean startElement;
    private int squareBrackets;

    public SimpleXmlWriter(OutputStream output) throws IOException {
        this.output = new OutputStreamWriter(output, "UTF-8");
        writeXmlDeclaration("UTF-8", "1.0");
    }

    private void writeXmlDeclaration(String encoding, String ver) throws IOException {
        write("<?xml version=\"");
        write(ver);
        write("\" encoding=\"");
        write(encoding);
        write("\"?>");
    }

    public void writeCharacters(String characters) throws IOException {
        maybeFinishElement();
        writeXmlEncoded(characters);
    }

    private void maybeFinishElement() throws IOException {
        if (startElement) {
            write(">");
            startElement = false;
        }
    }

    public SimpleXmlWriter writeStartElement(String name) throws IOException {
        if (!isValidXmlName(name)) {
            throw new IllegalArgumentException(String.format("Invalid element name: '%s'", name));
        }
        maybeFinishElement();
        startElement = true;
        elements.add(name);
        write("<");
        write(name);
        return this;
    }

    public void writeEndElement() throws IOException {
        if (elements.isEmpty()) {
            throw new IllegalStateException("Cannot write end element! There are no started elements.");
        }
        maybeFinishElement();
        write("</");
        write(elements.removeLast());
        write(">");
        if (elements.isEmpty()) {
            output.flush();
        }
    }

    public void writeEmptyElement(String name) throws IOException {
        maybeFinishElement();
        write("<");
        write(name);
        write("/>");
    }

    public void writeCDATA(char[] cdata) throws IOException {
        writeCDATA(cdata, 0, cdata.length);
    }

    public void writeCDATA(char[] cdata, int from, int to) throws IOException {
        for (int i = from; i < to; i++) {
            char c = cdata[i];
            if (needsCDATAEscaping(c)) {
                write("]]><![CDATA[>");
            } else {
                write(c);
            }
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
        maybeFinishElement();
        squareBrackets = 0;
        write("<![CDATA[");
    }

    public void writeEndCDATA() throws IOException {
        write("]]>");
    }

    public SimpleXmlWriter attribute(String name, String value) throws IOException {
        if (!isValidXmlName(name)) {
            throw new IllegalArgumentException(String.format("Invalid attribute name: '%s'", name));
        }
        if (!startElement) {
            throw new IllegalStateException("Cannot write attribute [" + name + ":" + value + "]. You should write start element first.");
        }

        write(" ");
        write(name);
        write("=\"");
        writeXmlEncoded(value);
        write("\"");
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

    private void write(char c) throws IOException {
        output.write(c);
    }

    private void write(String message) throws IOException {
        assert message != null;
        output.write(message);
    }

    private void writeXmlEncoded(CharSequence message) throws IOException {
        assert message != null;
        int len = message.length();
        for (int i = 0; i < len; i++) {
            char ch = message.charAt(i);
            if (ch == '<') {
                write("&lt;");
            } else if (ch == '>') {
                write("&gt;");
            } else if (ch == '&') {
                write("&amp;");
            } else if (ch == '"') {
                write("&quot;");
            } else {
                write(ch);
            }
        }
    }
}
