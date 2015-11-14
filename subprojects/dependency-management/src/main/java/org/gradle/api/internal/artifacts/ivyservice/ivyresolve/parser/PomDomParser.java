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

import com.ctc.wstx.stax.WstxInputFactory;
import org.codehaus.staxmate.dom.DOMConverter;
import org.gradle.internal.UncheckedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

    private static final XMLInputFactory XML_INPUT_FACTORY = createWoodstoxXmlInputFactory();
    private static final Map<String, String> M2_ENTITIES_MAP = new M2EntitiesMap();

    public static Document parseToDom(InputStream stream, String systemId) throws XMLStreamException, TransformerException, IOException, SAXException {
        final XMLStreamReader xmlStreamReader = XML_INPUT_FACTORY.createXMLStreamReader(systemId, stream);
        return stax2dom(decorateWithM2EntityReplacement(xmlStreamReader));
    }

    private static Document stax2dom(XMLStreamReader xmlStreamReader) throws XMLStreamException {
        final DocumentBuilder documentBuilder = createDocumentBuilder();
        return new DOMConverter(documentBuilder).buildDocument(xmlStreamReader, documentBuilder);
    }

    // Ivy supports handcrafted pom xml files that use HTML4 entities like &copy;
    // This is an efficient way to resolve the entities without using the m2entities.ent DTD injection hack that Ivy uses
    private static XMLStreamReader decorateWithM2EntityReplacement(final XMLStreamReader xmlStreamReader) throws XMLStreamException {
        return new EntityReplacementDelegate(xmlStreamReader, M2_ENTITIES_MAP);
    }

    // hard-code to use Woodstox StAX parser . IBM StAX parser doesn't support disabling IS_REPLACING_ENTITY_REFERENCES
    private static XMLInputFactory createWoodstoxXmlInputFactory() {
        XMLInputFactory inputFactory = new WstxInputFactory();
        inputFactory.setProperty(XMLInputFactory.IS_VALIDATING, false);
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return inputFactory;
    }

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = createDocumentBuilderFactory();

    private static DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        return factory;
    }

    private static DocumentBuilder createDocumentBuilder() {
        try {
            return DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static class EntityReplacementDelegate extends StreamReaderDelegate {
        private final Map<String, String> entitiesMap;
        private boolean replaceCurrentEvent;

        public EntityReplacementDelegate(XMLStreamReader xmlStreamReader, Map<String, String> entitiesMap) {
            super(xmlStreamReader);
            this.entitiesMap = entitiesMap;
        }

        @Override
        public int next() throws XMLStreamException {
            int eventType = super.next();
            if (eventType == XMLStreamConstants.ENTITY_REFERENCE) {
                replaceCurrentEvent = true;
                return XMLStreamConstants.CHARACTERS;
            } else {
                replaceCurrentEvent = false;
                return eventType;
            }
        }

        @Override
        public int getEventType() {
            return replaceCurrentEvent ? XMLStreamConstants.CHARACTERS : super.getEventType();
        }

        private boolean isReplaceCurrentEvent() {
            return replaceCurrentEvent;
        }

        @Override
        public boolean isCharacters() {
            return super.isCharacters() || isReplaceCurrentEvent();
        }

        @Override
        public boolean isWhiteSpace() {
            if (isReplaceCurrentEvent()) {
                return false;
            } else {
                return super.isWhiteSpace();
            }
        }

        @Override
        public String getText() {
            if (isReplaceCurrentEvent()) {
                String entityName = super.getLocalName();
                String replacement = entityName != null ? entitiesMap.get(entityName) : "";
                return replacement != null ? replacement : "";
            } else {
                return super.getText();
            }
        }

        @Override
        public char[] getTextCharacters() {
            if (isReplaceCurrentEvent()) {
                return getText().toCharArray();
            } else {
                return super.getTextCharacters();
            }
        }

        @Override
        public int getTextStart() {
            if (isReplaceCurrentEvent()) {
                return 0;
            } else {
                return super.getTextStart();
            }
        }

        @Override
        public int getTextLength() {
            if (isReplaceCurrentEvent()) {
                return getText().length();
            } else {
                return super.getTextLength();
            }
        }
    }
}
