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

package org.gradle.api.internal.tasks.testing.junit.result;

import org.gradle.util.TextUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;

import static org.apache.commons.lang.StringEscapeUtils.escapeXml;

/**
 * Basic xml writer. Provides basic validation and encoding.
 * Does not validate tag names (and some other things, too).
 *
 * by Szczepan Faber, created at: 12/3/12
 */
public class SimpleXmlWriter {

    private final Writer output;
    private final LinkedList<String> elements = new LinkedList<String>();
    private boolean writtenAnything;

    //add tag name / attribute name validation

    public SimpleXmlWriter(Writer output) {
        this.output = output;
    }

    public void writeXmlDeclaration(String encoding, String ver) throws IOException {
        if (writtenAnything) {
            throw new IllegalStateException("Cannot write xml declaration! The xml is not empty and the xml declaration must be the very first tag.");
        }
        write("<?xml version=\"");
        write(ver);
        write("\" encoding=\"");
        write(encoding);
        write("\"?>");
    }

    public void writeCharacters(String characters) throws IOException {
        writeEncoded(characters);
    }

    public void writeStartElement(String name) throws IOException {
        writeStartElement(element(name));
    }

    public void writeStartElement(Element element) throws IOException {
        elements.add(element.name);
        element.finished();
    }

    public void writeEmptyElement(String name) throws IOException {
        write("<");
        write(name);
        write("/>");
    }

    public void writeEndElement() throws IOException {
        if (elements.isEmpty()) {
            throw new IllegalStateException("Cannot write end element! There are no started elements.");
        }
        write("</");
        write(elements.removeLast());
        write(">");
    }

    //startCDATA, end,
    public void writeCDATA(Iterable<String> contents) throws IOException {
        write("<![CDATA[");
        for (String content : contents) {
            writeCDATAContent(content);
        }
        write("]]>");
    }

    private void writeCDATAContent(String cdata) throws IOException {
        write(TextUtil.escapeCDATA(cdata));
    }

    public Element element(String name) throws IOException {
        return new Element(name);
    }

    public class Element {

        private final String name;

        public Element(String name) throws IOException {
            this.name = name;
            write("<");
            write(name);
        }

        public Element attribute(String name, String value) throws IOException {
            write(" ");
            write(name);
            write("=\"");
            writeEncoded(value);
            write("\"");
            return this;
        }

        private void finished() throws IOException {
            write(">");
        }
    }

    private void write(String message) throws IOException {
        assert message != null;
        output.write(message);
        writtenAnything = true;
    }

    private void writeEncoded(String message) throws IOException {
        assert message != null;
        escapeXml(output, message);
        writtenAnything = true;
    }
}
