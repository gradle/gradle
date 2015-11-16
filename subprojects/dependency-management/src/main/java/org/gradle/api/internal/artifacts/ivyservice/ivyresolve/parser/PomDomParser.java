/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

public final class PomDomParser {
    private PomDomParser() {}

    public static String getTextContent(Element element) {
        StringBuilder result = new StringBuilder();

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);

            switch (child.getNodeType()) {
                case Node.CDATA_SECTION_NODE:
                case Node.TEXT_NODE:
                    result.append(child.getNodeValue());
                    break;
                default:
                    break;
            }
        }

        return result.toString();
    }

    public static String getFirstChildText(Element parentElem, String name) {
        Element node = getFirstChildElement(parentElem, name);
        if (node != null) {
            return getTextContent(node);
        } else {
            return null;
        }
    }

    public static Element getFirstChildElement(Element parentElem, String name) {
        if (parentElem == null) {
            return null;
        }
        NodeList childs = parentElem.getChildNodes();
        for (int i = 0; i < childs.getLength(); i++) {
            Node node = childs.item(i);
            if (node instanceof Element && name.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    public static List<Element> getAllChilds(Element parent) {
        List<Element> r = new LinkedList<Element>();
        if (parent != null) {
            NodeList childs = parent.getChildNodes();
            for (int i = 0; i < childs.getLength(); i++) {
                Node node = childs.item(i);
                if (node instanceof Element) {
                    r.add((Element) node);
                }
            }
        }
        return r;
    }

    public static final class AddDTDFilterInputStream extends FilterInputStream {
        private static final int MARK = 10000;
        private static final String DOCTYPE = "<!DOCTYPE project SYSTEM \"m2-entities.ent\">\n";

        private int count;
        private byte[] prefix = DOCTYPE.getBytes();

        public AddDTDFilterInputStream(InputStream in) throws IOException {
            super(new BufferedInputStream(in));

            this.in.mark(MARK);

            // TODO: we should really find a better solution for this...
            // maybe we could use a FilterReader instead of a FilterInputStream?
            int byte1 = this.in.read();
            int byte2 = this.in.read();
            int byte3 = this.in.read();

            if (byte1 == 239 && byte2 == 187 && byte3 == 191) {
                // skip the UTF-8 BOM
                this.in.mark(MARK);
            } else {
                this.in.reset();
            }

            int bytesToSkip = 0;
            LineNumberReader reader = new LineNumberReader(new InputStreamReader(this.in, "UTF-8"), 100);
            String firstLine = reader.readLine();
            if (firstLine != null) {
                String trimmed = firstLine.trim();
                if (trimmed.startsWith("<?xml ")) {
                    int endIndex = trimmed.indexOf("?>");
                    String xmlDecl = trimmed.substring(0, endIndex + 2);
                    prefix = (xmlDecl + "\n" + DOCTYPE).getBytes();
                    bytesToSkip = xmlDecl.getBytes().length;
                }
            }

            this.in.reset();
            for (int i = 0; i < bytesToSkip; i++) {
                this.in.read();
            }
        }

        public int read() throws IOException {
            if (count < prefix.length) {
                return prefix[count++];
            }

            return super.read();
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if ((off < 0) || (off > b.length) || (len < 0)
                    || ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }

            int nbrBytesCopied = 0;

            if (count < prefix.length) {
                int nbrBytesFromPrefix = Math.min(prefix.length - count, len);
                System.arraycopy(prefix, count, b, off, nbrBytesFromPrefix);
                nbrBytesCopied = nbrBytesFromPrefix;
            }

            if (nbrBytesCopied < len) {
                nbrBytesCopied += in.read(b, off + nbrBytesCopied, len - nbrBytesCopied);
            }

            count += nbrBytesCopied;
            return nbrBytesCopied;
        }
    }
}
