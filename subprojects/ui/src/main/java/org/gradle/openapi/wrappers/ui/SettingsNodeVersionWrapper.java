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
package org.gradle.openapi.wrappers.ui;

import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.openapi.external.ui.SettingsNodeVersion1;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Wrapper to shield version changes in SettingsNode from an external user of gradle open API.
 */
public class SettingsNodeVersionWrapper implements SettingsNode {
    private SettingsNodeVersion1 settingsNodeVersion1;

    SettingsNodeVersionWrapper(SettingsNodeVersion1 settingsNodeVersion1) {
        this.settingsNodeVersion1 = settingsNodeVersion1;

        //when future versions are added, doing the following and then delegating
        //the new functions to SettingsNodeVersion2 keeps things compatible.
        //if( settingsNodeVersion1 instanceof SettingsNodeVersion2 )
        //   settingsNodeVersion2 = (SettingsNodeVersion2) settingsNodeVersion1;
    }

    public void setName(String name) {
        settingsNodeVersion1.setName(name);
    }

    public String getName() {
        return settingsNodeVersion1.getName();
    }

    public void setValue(String value) {
        settingsNodeVersion1.setValue(value);
    }

    public String getValue() {
        return settingsNodeVersion1.getValue();
    }

    public void setValueOfChild(String name, String value) {
        settingsNodeVersion1.setValueOfChild(name, value);
    }

    public String getValueOfChild(String name, String defaultValue) {
        return settingsNodeVersion1.getValueOfChild(name, defaultValue);
    }

    public int getValueOfChildAsInt(String name, int defaultValue) {
        return settingsNodeVersion1.getValueOfChildAsInt(name, defaultValue);
    }

    public void setValueOfChildAsInt(String name, int value) {
        settingsNodeVersion1.setValueOfChildAsInt(name, value);
    }

    public boolean getValueOfChildAsBoolean(String name, boolean defaultValue) {
        return settingsNodeVersion1.getValueOfChildAsBoolean(name, defaultValue);
    }

    public void setValueOfChildAsBoolean(String name, boolean value) {
        settingsNodeVersion1.setValueOfChildAsBoolean(name, value);
    }

    public long getValueOfChildAsLong(String name, long defaultValue) {
        return settingsNodeVersion1.getValueOfChildAsLong(name, defaultValue);
    }

    public void setValueOfChildAsLong(String name, long value) {
        settingsNodeVersion1.setValueOfChildAsLong(name, value);
    }

    public SettingsNode getChildNode(String name) {
        SettingsNodeVersion1 childNode = settingsNodeVersion1.getChildNode(name);
        if (childNode == null) {
            return null;
        }

        return new SettingsNodeVersionWrapper(childNode);
    }

    public List<SettingsNode> getChildNodes() {
        return convertNodes(settingsNodeVersion1.getChildNodes());
    }

    public List<SettingsNode> getChildNodes(String name) {
        return convertNodes(settingsNodeVersion1.getChildNodes(name));
    }

    /*package*/

    static List<SettingsNode> convertNodes(List<SettingsNodeVersion1> nodes) {
        List<SettingsNode> settingsNodes = new ArrayList<SettingsNode>();

        Iterator<SettingsNodeVersion1> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            SettingsNodeVersion1 nodeVersion1 = iterator.next();
            settingsNodes.add(new SettingsNodeVersionWrapper(nodeVersion1));
        }

        return settingsNodes;
    }

    public SettingsNode addChild(String name) {
        return new SettingsNodeVersionWrapper(settingsNodeVersion1.addChild(name));
    }

    public SettingsNode addChildIfNotPresent(String name) {
        return new SettingsNodeVersionWrapper(settingsNodeVersion1.addChildIfNotPresent(name));
    }

    public SettingsNode getNodeAtPath(String... pathPortions) {
        SettingsNodeVersion1 node = settingsNodeVersion1.getNodeAtPath(pathPortions);
        if (node == null) {
            return null;
        }
        return new SettingsNodeVersionWrapper(node);
    }

    public void removeFromParent() {
        settingsNodeVersion1.removeFromParent();
    }

    public void removeAllChildren() {
        settingsNodeVersion1.removeAllChildren();
    }

    @Override
    public String toString() {
        return settingsNodeVersion1.toString();
    }
}
