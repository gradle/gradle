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

    public SimpleXmlWriter(Writer output) {
        this.output = output;
    }

    public void writeXmlDeclaration(String encoding, String ver) throws IOException {
        if (writtenAnything) {
            throw new IllegalStateException("Cannot write xml declaration! The xml is not empty and the xml declaration must be the very first tag.");
        }
        write("<?xml version=\"" + ver + "\" encoding=\"" + encoding + "\"?>");
    }

    public void writeCharacters(String characters) throws IOException {
        write(encodeXml(characters));
    }

    public void writeStartElement(Element element) throws IOException {
        write(element.toXML());
        elements.add(element.name);
    }

    public void writeEmptyElement(String name) throws IOException {
        write("<" + name + "/>");
    }

    public void writeEndElement() throws IOException {
        if (elements.isEmpty()) {
            throw new IllegalStateException("Cannot write end element! There are no started elements.");
        }
        write("</" + elements.removeLast() + ">");
    }

    public static class Element {

        private final StringBuilder output;

        private final String name;
        public Element(String name) {
            this.name = name;
            this.output = new StringBuilder("<").append(name);
        }

        public Element attribute(String name, String value) {
            output.append(" ").append(name).append("=\"").append(encodeXml(value)).append("\"");
            return this;
        }

        public String toXML() {
            return output.toString() + ">";
        }

    }

    private static String encodeXml(String value) {
        return escapeXml(value);
    }

    private void write(String message) throws IOException {
        assert message != null;
        writtenAnything = true;
        output.write(message);
    }
}
