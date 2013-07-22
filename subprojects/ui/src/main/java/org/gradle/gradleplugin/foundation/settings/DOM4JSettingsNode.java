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
package org.gradle.gradleplugin.foundation.settings;

import org.dom4j.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of SettingsNode that uses DOM4J nodes as its actual storage medium.
 */
public class DOM4JSettingsNode implements SettingsNode {
    public static final String TAG_NAME = "setting";
    public static final String NAME_ATTRIBUTE = "name";
    public static final String VALUE_ATTRIBUTE = "value";
    private Element element;

    public DOM4JSettingsNode(Element element) {
        this.element = element;
    }

    public Element getElement() {
        return element;
    }

    public void setName(String name) {
        element.addAttribute(NAME_ATTRIBUTE, name);
    }

    public String getName() {
        return element.attributeValue(NAME_ATTRIBUTE);
    }

    public void setValue(String value) {
        element.addAttribute(VALUE_ATTRIBUTE, value);
    }

    public String getValue() {
        return element.attributeValue(VALUE_ATTRIBUTE);
    }

    public void setValueOfChild(String name, String value) {
        SettingsNode settingsNode = addChildIfNotPresent(name);
        settingsNode.setValue(value);
    }

    public String getValueOfChild(String name, String defaultValue) {
        SettingsNode settingsNode = getChildNode(name);
        if (settingsNode != null) {
            String value = settingsNode.getValue();
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    public SettingsNode getChildNode(String name) {
        Iterator iterator = element.elements().iterator();
        while (iterator.hasNext()) {
            Element childElement = (Element) iterator.next();
            if (name.equals(childElement.attributeValue(NAME_ATTRIBUTE))) {
                return new DOM4JSettingsNode(childElement);
            }
        }
        return null;
    }

    public List<SettingsNode> getChildNodes() {
        return convertNodes(element.elements());
    }

    private List<SettingsNode> convertNodes(List elements) {
        List<SettingsNode> children = new ArrayList<SettingsNode>();

        Iterator iterator = elements.iterator();
        while (iterator.hasNext()) {
            Element childElement = (Element) iterator.next();
            children.add(new DOM4JSettingsNode(childElement));
        }

        return children;
    }

    public List<SettingsNode> getChildNodes(String name) {
        List<SettingsNode> children = new ArrayList<SettingsNode>();

        Iterator iterator = element.elements().iterator();
        while (iterator.hasNext()) {
            Element childElement = (Element) iterator.next();
            if (name.equals(childElement.attributeValue(NAME_ATTRIBUTE))) {
                children.add(new DOM4JSettingsNode(childElement));
            }
        }

        return children;
    }

    public int getValueOfChildAsInt(String name, int defaultValue) {
        SettingsNode settingsNode = getChildNode(name);
        if (settingsNode != null) {
            String value = settingsNode.getValue();

            try {
                if (value != null) {
                    return Integer.parseInt(value);
                }
            } catch (NumberFormatException e) {
                //we couldn't parse it. Just return the default.
            }
        }
        return defaultValue;
    }

    public void setValueOfChildAsInt(String name, int value) {
        setValueOfChild(name, Integer.toString(value));
    }

    public long getValueOfChildAsLong(String name, long defaultValue) {
        SettingsNode settingsNode = getChildNode(name);
        if (settingsNode != null) {
            String value = settingsNode.getValue();

            try {
                if (value != null) {
                    return Long.parseLong(value);
                }
            } catch (NumberFormatException e) {
                //we couldn't parse it. Just return the default.
            }
        }
        return defaultValue;
    }

    public void setValueOfChildAsLong(String name, long value) {
        setValueOfChild(name, Long.toString(value));
    }

    public boolean getValueOfChildAsBoolean(String name, boolean defaultValue) {
        SettingsNode settingsNode = getChildNode(name);
        if (settingsNode != null) {
            String value = settingsNode.getValue();

            //I'm not calling 'Boolean.parseBoolean( value )' because it will return false if the value isn't true/false
            //and we want it to return whatever the default is if its not a boolean.
            if (value != null) {
                if ("true".equalsIgnoreCase(value)) {
                    return true;
                }

                if ("false".equalsIgnoreCase(value)) {
                    return false;
                }
            }
        }

        return defaultValue;
    }

    public void setValueOfChildAsBoolean(String name, boolean value) {
        setValueOfChild(name, Boolean.toString(value));
    }

    public SettingsNode addChild(String name) {
        DOM4JSettingsNode childElement = new DOM4JSettingsNode(element.addElement(TAG_NAME));

        childElement.setName(name);
        return childElement;
    }

    public SettingsNode addChildIfNotPresent(String name) {
        SettingsNode settingsNode = getChildNode(name);
        if (settingsNode == null) {
            settingsNode = addChild(name);
        }

        return settingsNode;
    }

    public SettingsNode getNodeAtPath(String... pathPortions) {
        if (pathPortions == null || pathPortions.length == 0) {
            return null;
        }

        String firstPathPortion = pathPortions[0];

        SettingsNode currentNode = getChildNode(firstPathPortion);

        int index = 1; //Skip the first one. we've already used that one.
        while (index < pathPortions.length && currentNode != null) {
            String pathPortion = pathPortions[index];
            currentNode = currentNode.getChildNode(pathPortion);
            index++;
        }

        return currentNode;
    }

    private SettingsNode getNodeAtPathCreateIfNotFound(String... pathPortions) {
        if (pathPortions == null || pathPortions.length == 0) {
            return null;
        }

        String firstPathPortion = pathPortions[0];

        SettingsNode currentNode = getChildNode(firstPathPortion);
        if (currentNode == null) {
            currentNode = addChild(firstPathPortion);
        }

        int index = 1;
        while (index < pathPortions.length) {
            String pathPortion = pathPortions[index];
            currentNode = currentNode.getChildNode(pathPortion);
            if (currentNode == null) {
                currentNode = addChild(firstPathPortion);
            }

            index++;
        }

        return currentNode;
    }

    public void removeFromParent() {
        element.detach();
    }

    public void removeAllChildren() {
        List list = element.elements();
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Element child = (Element) iterator.next();
            child.detach();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DOM4JSettingsNode)) {
            return false;
        }

        DOM4JSettingsNode otherNode = (DOM4JSettingsNode) obj;

        //we're the same if our elements are the same.
        return otherNode.element.equals(element);
    }

    @Override
    public int hashCode() {
        return element.hashCode();
    }

    @Override
    public String toString() {
        return getName() + "='" + getValue() + "' " + element.elements().size() + " children";
    }
}
