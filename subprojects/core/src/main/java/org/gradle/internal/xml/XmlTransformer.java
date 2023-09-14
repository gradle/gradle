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
package org.gradle.internal.xml;

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.util.IndentPrinter;
import groovy.util.Node;
import groovy.xml.XmlNodePrinter;
import groovy.xml.XmlParser;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.XmlProvider;
import org.gradle.api.internal.DomNode;
import org.gradle.internal.IoActions;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.util.internal.GUtil;
import org.gradle.util.internal.TextUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class XmlTransformer implements Transformer<String, String> {
    private final List<Action<? super XmlProvider>> actions = new ArrayList<Action<? super XmlProvider>>();
    private final List<Action<? super XmlProvider>> finalizers = Lists.newArrayListWithExpectedSize(2);
    private String indentation = "  ";

    public void addAction(Action<? super XmlProvider> provider) {
        actions.add(provider);
    }

    public void addFinalizer(Action<? super XmlProvider> provider) {
        finalizers.add(provider);
    }

    public void setIndentation(String indentation) {
        this.indentation = indentation;
    }

    public void addAction(@DelegatesTo(XmlProvider.class) Closure closure) {
        actions.add(ConfigureUtil.configureUsing(closure));
    }

    public void transform(File destination, final String encoding, final Action<? super Writer> generator) {
        IoActions.writeTextFile(destination, encoding, new Action<Writer>() {
            @Override
            public void execute(Writer writer) {
                transform(writer, encoding, generator);
            }
        });
    }

    public void transform(File destination, final Action<? super Writer> generator) {
        IoActions.writeTextFile(destination, new Action<Writer>() {
            @Override
            public void execute(Writer writer) {
                transform(writer, generator);
            }
        });
    }

    public void transform(Writer destination, Action<? super Writer> generator) {
        StringWriter stringWriter = new StringWriter();
        generator.execute(stringWriter);
        transform(stringWriter.toString(), destination);
    }

    public void transform(Writer destination, String encoding, Action<? super Writer> generator) {
        StringWriter stringWriter = new StringWriter();
        generator.execute(stringWriter);
        doTransform(stringWriter.toString()).writeTo(destination, encoding);
    }

    @Override
    public String transform(String original) {
        return doTransform(original).toString();
    }

    public void transform(String original, Writer destination) {
        doTransform(original).writeTo(destination);
    }

    public void transform(String original, OutputStream destination) {
        doTransform(original).writeTo(destination);
    }

    public void transform(Node original, Writer destination) {
        doTransform(original).writeTo(destination);
    }

    public void transform(Node original, OutputStream destination) {
        doTransform(original).writeTo(destination);
    }

    public void transform(Node original, File destination) {
        doTransform(original).writeTo(destination);
    }

    public void transform(DomNode original, Writer destination) {
        doTransform(original).writeTo(destination);
    }

    public void transform(DomNode original, OutputStream destination) {
        doTransform(original).writeTo(destination);
    }

    private XmlProviderImpl doTransform(String original) {
        return doTransform(new XmlProviderImpl(original));
    }

    private XmlProviderImpl doTransform(Node original) {
        return doTransform(new XmlProviderImpl(original));
    }

    private XmlProviderImpl doTransform(DomNode original) {
        return doTransform(new XmlProviderImpl(original));
    }

    private XmlProviderImpl doTransform(XmlProviderImpl provider) {
        provider.apply(actions);
        provider.apply(finalizers);
        return provider;
    }

    private class XmlProviderImpl implements XmlProvider {
        private StringBuilder builder;
        private Node node;
        private String stringValue;
        private Element element;
        private String publicId;
        private String systemId;

        public XmlProviderImpl(String original) {
            this.stringValue = original;
        }

        public XmlProviderImpl(Node original) {
            this.node = original;
        }

        public XmlProviderImpl(DomNode original) {
            this.node = original;
            publicId = original.getPublicId();
            systemId = original.getSystemId();
        }

        public void apply(Iterable<Action<? super XmlProvider>> actions) {
            for (Action<? super XmlProvider> action : actions) {
                action.execute(this);
            }
        }

        @Override
        public String toString() {
            StringWriter writer = new StringWriter();
            writeTo(writer);
            return writer.toString();
        }

        public void writeTo(Writer writer) {
            doWriteTo(writer, null);
        }

        public void writeTo(Writer writer, String encoding) {
            doWriteTo(writer, encoding);
        }

        public void writeTo(File file) {
            try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(file.toPath()))) {
                writeTo(outputStream);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        public void writeTo(OutputStream stream) {
            try(Writer writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
                doWriteTo(writer, "UTF-8");
                writer.flush();
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public StringBuilder asString() {
            if (builder == null) {
                builder = new StringBuilder(toString());
                node = null;
                element = null;
            }
            return builder;
        }

        @Override
        public Node asNode() {
            if (node == null) {
                try {
                    node = new XmlParser().parseText(toString());
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                builder = null;
                element = null;
            }
            return node;
        }

        @Override
        public Element asElement() {
            if (element == null) {
                Document document;
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                    document = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(toString())));
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                element = document.getDocumentElement();
                builder = null;
                node = null;
            }
            return element;
        }

        private void doWriteTo(Writer writer, String encoding) {
            writeXmlDeclaration(writer, encoding);

            try {
                if (node != null) {
                    printNode(node, writer);
                } else if (element != null) {
                    printDomNode(element, writer);
                } else if (builder != null) {
                    writer.append(TextUtil.toPlatformLineSeparators(stripXmlDeclaration(builder)));
                } else {
                    writer.append(TextUtil.toPlatformLineSeparators(stripXmlDeclaration(stringValue)));
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        private void printNode(Node node, Writer writer) {
            final PrintWriter printWriter = new PrintWriter(writer);
            if (GUtil.isTrue(publicId)) {
                printWriter.format("<!DOCTYPE %s PUBLIC \"%s\" \"%s\">%n", node.name(), publicId, systemId);
            }
            IndentPrinter indentPrinter = new IndentPrinter(printWriter, indentation) {
                @Override
                public void println() {
                    printWriter.println();
                }

                @Override
                public void flush() {
                    // for performance, ignore flushes
                }
            };
            XmlNodePrinter nodePrinter = new XmlNodePrinter(indentPrinter);
            nodePrinter.setPreserveWhitespace(true);
            nodePrinter.print(node);
            printWriter.flush();
        }

        private void printDomNode(org.w3c.dom.Node node, Writer destination) {
            removeEmptyTextNodes(node); // empty text nodes hinder subsequent formatting
            int indentAmount = determineIndentAmount();

            try {
                TransformerFactory factory = TransformerFactory.newInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                try {
                    factory.setAttribute("indent-number", indentAmount);
                } catch (IllegalArgumentException ignored) {
                    /* unsupported by this transformer */
                }

                javax.xml.transform.Transformer transformer = factory.newTransformer();
                transformer.setOutputProperty(OutputKeys.METHOD, "xml");
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                if (GUtil.isTrue(publicId)) {
                    transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, publicId);
                    transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemId);
                }
                try {
                    // some impls support this but not factory.setAttribute("indent-number")
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indentAmount));
                } catch (IllegalArgumentException ignored) {
                    /* unsupported by this transformer */
                }

                transformer.transform(new DOMSource(node), new StreamResult(destination));
            } catch (TransformerException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        private int determineIndentAmount() {
            if (indentation.equals("\t")) { // not supported by javax.xml.transform.Transformer; use two spaces instead
                return 2;
            }
            return indentation.length(); // assume indentation uses spaces
        }

        private void removeEmptyTextNodes(org.w3c.dom.Node node) {
            org.w3c.dom.NodeList children = node.getChildNodes();

            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE && child.getNodeValue().trim().length() == 0) {
                    node.removeChild(child);
                    i--;
                } else {
                    removeEmptyTextNodes(child);
                }
            }
        }

        private void writeXmlDeclaration(Writer writer, String encoding) {
            try {
                writer.write("<?xml version=\"1.0\"");
                if (encoding != null) {
                    writer.write(" encoding=\"");
                    writer.write(encoding);
                    writer.write("\"");
                }
                writer.write("?>");
                writer.write(SystemProperties.getInstance().getLineSeparator());
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        private boolean hasXmlDeclaration(String xml) {
            return xml.startsWith("<?xml"); // XML declarations must be located at first position of first line
        }

        private String stripXmlDeclaration(CharSequence sequence) {
            String str = sequence.toString();
            if (hasXmlDeclaration(str)) {
                str = str.substring(str.indexOf("?>") + 2);
                str = StringUtils.stripStart(str, null);
            }
            return str;
        }
    }
}
