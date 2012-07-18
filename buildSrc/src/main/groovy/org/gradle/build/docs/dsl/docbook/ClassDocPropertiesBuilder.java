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

package org.gradle.build.docs.dsl.docbook;

import org.gradle.build.docs.dsl.docbook.model.ExtraAttributeDoc;
import org.gradle.build.docs.dsl.docbook.model.PropertyDoc;
import org.gradle.build.docs.dsl.source.model.PropertyMetaData;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.util.*;

public class ClassDocPropertiesBuilder {
    private final DslDocModel model;
    private final JavadocConverter javadocConverter;
    private final GenerationListener listener;

    public ClassDocPropertiesBuilder(DslDocModel model, JavadocConverter javadocConverter, GenerationListener listener) {
        this.model = model;
        this.javadocConverter = javadocConverter;
        this.listener = listener;
    }

    void build(ClassDoc classDoc) {
        Element thead = getChild(classDoc.getPropertiesTable(), "thead");
        Element tr = getChild(thead, "tr");
        List<Element> header = children(tr, "td");
        if (header.size() < 1) {
            throw new RuntimeException(String.format("Expected at least 1 <td> in <thead>/<tr>, found: %s", header));
        }
        Map<String, Element> inheritedValueTitleMapping = new HashMap<String, Element>();
        List<Element> valueTitles = new ArrayList<Element>();
        for (int i = 1; i < header.size(); i++) {
            Element element = header.get(i);
            Element override = findChild(element, "overrides");
            if (override != null) {
                element.removeChild(override);
                inheritedValueTitleMapping.put(override.getTextContent(), element);
            }
            Node firstChild = element.getFirstChild();
            if (firstChild instanceof Text) {
                firstChild.setTextContent(firstChild.getTextContent().replaceFirst("^\\s+", ""));
            }
            Node lastChild = element.getLastChild();
            if (lastChild instanceof Text) {
                lastChild.setTextContent(lastChild.getTextContent().replaceFirst("\\s+$", ""));
            }
            valueTitles.add(element);
        }

        String superClassName = classDoc.getClassMetaData().getSuperClassName();
        ClassDoc superClass = superClassName != null ? model.getClassDoc(superClassName) : null;

        //adding the properties from the super class onto the inheriting class
        Map<String, PropertyDoc> props = new TreeMap<String, PropertyDoc>();
        if (superClass != null) {
            for (PropertyDoc propertyDoc : superClass.getClassProperties()) {
                Map<String, ExtraAttributeDoc> additionalValues = new LinkedHashMap<String, ExtraAttributeDoc>();
                for (ExtraAttributeDoc attributeDoc : propertyDoc.getAdditionalValues()) {
                    String key = attributeDoc.getKey();
                    if (inheritedValueTitleMapping.get(key) != null) {
                        ExtraAttributeDoc newAttribute = new ExtraAttributeDoc(inheritedValueTitleMapping.get(key), attributeDoc.getValueCell());
                        additionalValues.put(newAttribute.getKey(), newAttribute);
                    } else {
                        additionalValues.put(key, attributeDoc);
                    }
                }

                props.put(propertyDoc.getName(), propertyDoc.forClass(classDoc.getClassMetaData(), additionalValues.values()));
            }
        }

        for (Element row : children(classDoc.getPropertiesTable(), "tr")) {
            List<Element> cells = children(row, "td");
            if (cells.size() != header.size()) {
                throw new RuntimeException(String.format("Expected %s <td> elements in <tr>, found: %s", header.size(), tr));
            }
            String propName = cells.get(0).getTextContent().trim();
            PropertyMetaData property = classDoc.getClassMetaData().findProperty(propName);
            if (property == null) {
                throw new RuntimeException(String.format("No metadata for property '%s.%s'. Available properties: %s", classDoc.getName(), propName, classDoc.getClassMetaData().getPropertyNames()));
            }

            Map<String, ExtraAttributeDoc> additionalValues = new LinkedHashMap<String, ExtraAttributeDoc>();

            if (superClass != null) {
                PropertyDoc overriddenProp = props.get(propName);
                if (overriddenProp != null) {
                    for (ExtraAttributeDoc attributeDoc : overriddenProp.getAdditionalValues()) {
                        additionalValues.put(attributeDoc.getKey(), attributeDoc);
                    }
                }
            }

            for (int i = 1; i < header.size(); i++) {
                if (cells.get(i).getFirstChild() == null) {
                    continue;
                }
                ExtraAttributeDoc attributeDoc = new ExtraAttributeDoc(valueTitles.get(i - 1), cells.get(i));
                additionalValues.put(attributeDoc.getKey(), attributeDoc);
            }
            PropertyDoc propertyDoc = new PropertyDoc(property, javadocConverter.parse(property, listener).getDocbook(), new ArrayList<ExtraAttributeDoc>(additionalValues.values()));
            if (propertyDoc.getDescription() == null) {
                throw new RuntimeException(String.format("Docbook content for '%s.%s' does not contain a description paragraph.", classDoc.getName(), propName));
            }

            props.put(propName, propertyDoc);
        }

        for (PropertyDoc propertyDoc : props.values()) {
            classDoc.addClassProperty(propertyDoc);
        }
    }

    private List<Element> children(Element element, String childName) {
        List<Element> matches = new ArrayList<Element>();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element) {
                Element childElement = (Element) node;
                if (childElement.getTagName().equals(childName)) {
                    matches.add(childElement);
                }
            }
        }
        return matches;
    }

    private Element getChild(Element element, String childName) {
        Element child = findChild(element, childName);
        if (child != null) {
            return child;
        }
        throw new RuntimeException(String.format("No <%s> element found in <%s>", childName, element.getTagName()));
    }

    private Element findChild(Element element, String childName) {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element) {
                Element childElement = (Element) node;
                if (childElement.getTagName().equals(childName)) {
                    return childElement;
                }
            }
        }
        return null;
    }
}
