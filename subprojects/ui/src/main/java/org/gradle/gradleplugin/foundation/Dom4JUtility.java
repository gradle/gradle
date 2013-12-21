/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.gradleplugin.foundation;

import org.dom4j.Attribute;
import org.dom4j.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/*
 Just some utility functions that really should be in Dom4J already.

  */

public class Dom4JUtility {
    /**
     * This returns the node that is a child of the specified parent that has the specified 'name' and an attribute with the specified value. This is similar to the below getChild, but this requires a
     * specific tag name.
     */
    public static Element getChild(Element parent, String tagName, String attribute, String attributeValue) {
        Element childElement = null;
        Iterator iterator = parent.elements(tagName).iterator();
        while (iterator.hasNext() && childElement == null) {
            childElement = (Element) iterator.next();
            String actualValue = childElement.attributeValue(attribute);
            if (!attributeValue.equals(actualValue)) {
                childElement = null;
            }
        }
        return childElement;
    }

    public static List<Element> getChildren(Element parent, String tagName, String attribute, String attributeValue) {
        List<Element> children = new ArrayList<Element>();

        Iterator iterator = parent.elements(tagName).iterator();
        while (iterator.hasNext()) {
            Element childElement = (Element) iterator.next();
            String actualValue = childElement.attributeValue(attribute);
            if (attributeValue.equals(actualValue)) {
                children.add(childElement);
            }
        }

        return children;
    }

    /**
     * Thie returns the node that is a child of hte specified parent that has the specified attribute with the specified value. This is similar to the above getChild, but no tag name is required.
     */
    public static Element getChild(Element parent, String attribute, String attributeValue) {
        Element childElement = null;
        Iterator iterator = parent.elements().iterator();
        while (iterator.hasNext() && childElement == null) {
            childElement = (Element) iterator.next();
            String actualValue = childElement.attributeValue(attribute);
            if (!attributeValue.equals(actualValue)) {
                childElement = null;
            }
        }
        return childElement;
    }

    /**
     * Thie returns the node that is a child of hte specified parent that has the specified attribute with the specified value. This is similar to the above getChild, but no tag name is required.
     */
    public static List<Element> getChildren(Element parent, String attribute, String attributeValue) {
        List<Element> children = new ArrayList<Element>();

        Iterator iterator = parent.elements().iterator();
        while (iterator.hasNext()) {
            Element childElement = (Element) iterator.next();
            String actualValue = childElement.attributeValue(attribute);
            if (attributeValue.equals(actualValue)) {
                children.add(childElement);
            }
        }

        return children;
    }

    public static void setAttributeAsBoolean(Element element, String attribute, boolean value) {
        if (value) {
            element.addAttribute(attribute, "true");
        } else {
            element.addAttribute(attribute, "false");
        }
    }

    public static boolean getAttributeAsBoolean(Element element, String attributeName, boolean defaultValue) {
        Attribute attribute = element.attribute(attributeName);
        if (attribute == null) {
            return defaultValue;
        }

        return "true".equals(attribute.getValue());
    }
}
