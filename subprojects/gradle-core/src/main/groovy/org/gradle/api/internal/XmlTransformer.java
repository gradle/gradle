/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal;

import groovy.util.Node;
import groovy.util.XmlNodePrinter;
import groovy.util.XmlParser;
import groovy.xml.XmlUtil;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.maven.XmlProvider;
import org.gradle.util.UncheckedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class XmlTransformer implements Transformer<String> {
    private final List<Action<? super XmlProvider>> actions = new ArrayList<Action<? super XmlProvider>>();

    public void addAction(Action<? super XmlProvider> provider) {
        actions.add(provider);
    }

    public String transform(String original) {
        return doTransform(original).toString();
    }

    public void transform(String original, Writer destination) {
        doTransform(original).writeTo(destination);
    }

    private XmlProviderImpl doTransform(String original) {
        XmlProviderImpl provider = new XmlProviderImpl(original);
        for (Action<? super XmlProvider> action : actions) {
            action.execute(provider);
        }
        return provider;
    }

    private static class XmlProviderImpl implements XmlProvider {
        private StringBuilder builder;
        private Node node;
        private String stringValue;
        private Element element;

        public XmlProviderImpl(String original) {
            this.stringValue = original;
        }

        @Override
        public String toString() {
            StringWriter writer = new StringWriter();
            writeTo(writer);
            return writer.toString();
        }

        public void writeTo(Writer writer) {
            try {
                if (node != null) {
                    PrintWriter printWriter = new PrintWriter(writer);
                    new XmlNodePrinter(printWriter).print(node);
                    printWriter.flush();
                } else if (element != null) {
                    PrintWriter printWriter = new PrintWriter(writer);
                    XmlUtil.serialize(element, printWriter);
                    printWriter.flush();
                } else if (builder != null) {
                    writer.append(builder);
                } else {
                    writer.append(stringValue);
                }
            } catch (IOException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }

        public StringBuilder asString() {
            if (builder == null) {
                builder = new StringBuilder(toString());
                node = null;
                element = null;
            }
            return builder;
        }

        public Node asNode() {
            if (node == null) {
                try {
                    node = new XmlParser().parseText(toString());
                } catch (Exception e) {
                    throw UncheckedException.asUncheckedException(e);
                }
                builder = null;
                element = null;
            }
            return node;
        }

        public Element asElement() {
            if (element == null) {
                Document document;
                try {
                    document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(toString())));
                } catch (Exception e) {
                    throw UncheckedException.asUncheckedException(e);
                }
                element = document.getDocumentElement();
                builder = null;
                node = null;
            }
            return element;
        }
    }
}
