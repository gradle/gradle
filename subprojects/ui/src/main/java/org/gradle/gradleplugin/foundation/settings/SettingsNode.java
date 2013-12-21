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
package org.gradle.gradleplugin.foundation.settings;

import java.util.List;

/**
 * This provides a mechanism for storing settings. It is a hybrid of a DOM (like xml) and something much simpler like the java preferences. It is first meant to be easy to use. Second, it is supposed
 * to abstract how the settings are actually stored. The point is to allow IDEs and such to store this is whatever manner they choose. Thus, this is vague. Third, it is meant to be relatively easy to
 * implement.
 *
 * While this is a hiearchy, it is not meant to be as complex as XML with arbitrary attributes. Instead, it is only meant to be a key-value pairing. However, the hiearchy allows you to easily have
 * lists and other complex structures without having to worry about name collisions as you would if this were a pure key-value pair.
 *
 * This is meant to be a single tree for the entire application. You shouldn't create these on your own. A node for your use should be given to you by your parent. Only the highest level object should
 * directly create an instance of one of these. It should manage saving and restore these settings.
 *
 * Due to how some 'owners' of the settings work, you should save your settings immediately here (think of this as you would a database). Why? Well, for example: when Idea is the owner in the gradle
 * Idea plugin, it attempts to store its settings very frequently and uses differences in the results to determine if changes have been made to a plugin. As a result, we store things in the setting
 * immediately.
 *
 * This node consists to 3 things: - name: this is the 'key' of key-value pair. It is required. - value: this is the 'value' of key-value pair. It is NOT required. - child nodes: each node can have
 * children nodes.
 *
 * Using these you can create a tree structure storing whatever you like. If you need multiple attributes like XML has, you should create children instead. So if, in xml, you wanted to do:
 *
 * <setting name="myname" value="myvalue" myotherattribute="attribute1" somethingelse="attribute2" >
 *
 * do this instead:
 *
 * node name="myName" value="myvalue" node name="myotherattribute" value="attribute1" node name="somethingelse" value="attribute2"
 *
 * This has several convenience functions for setting and getting values from child nodes. These are meant to be used in more of a java preferences replacement. You should create your own root node of
 * your settings (by call addChildIfNotPresent from a node that is given to you) then you can use these functions and you only need to worry about uniqueness within your own node.
 */
public interface SettingsNode {
    /**
     * Sets the name of this node. This is used as its identifier.
     *
     * @param name the new name. Cannot be null!
     */
    public void setName(String name);

    public String getName();

    /**
     * Sets the value of this node. This is whatever you like, but is always internally text.
     *
     * @param value the new value. Can be null.
     */
    public void setValue(String value);

    public String getValue();

    /**
     * Sets the value of the child node, adding it if it is not already present. This is a convenience function providing more java preferences-like behavior.
     *
     * @param name the name of the child node.
     * @param value the new value.
     */
    public void setValueOfChild(String name, String value);

    /**
     * Gets the value of the child node. If it is not present, the defaultValue is returned. This is a convenience function providing more java preferences-like behavior.
     *
     * @param name the name of the child node.
     * @param defaultValue the value to return if the child node is not present
     * @return the value.
     */
    public String getValueOfChild(String name, String defaultValue);

    /**
     * Sets the value of the child node as an integer, adding it if it is not already present. This is a convenience function providing more java preferences-like behavior.
     *
     * @param name the name of the child node.
     * @param value the new value.
     */
    public void setValueOfChildAsInt(String name, int value);

    /**
     * Gets the value of the child node as an integer. If it is not present or the value is cannot be interpretted as an integer, the defaultValue is returned. This is a convenience function providing
     * more java preferences-like behavior.
     *
     * @param name the name of the child node.
     * @param defaultValue the value to return if the child node is not present or cannot be interpretted as an integer.
     * @return the value.
     */
    public int getValueOfChildAsInt(String name, int defaultValue);

    //same as setValueOfChildAsInt but with a boolean

    public void setValueOfChildAsBoolean(String name, boolean value);

    //same as getValueOfChildAsInt but with a boolean

    public boolean getValueOfChildAsBoolean(String name, boolean defaultValue);

    //same as setValueOfChildAsInt but with a long

    public void setValueOfChildAsLong(String name, long value);

    //same as getValueOfChildAsInt but with a long

    public long getValueOfChildAsLong(String name, long defaultValue);

    /**
     * @return a list of all child nodes of this node.
     */
    public List<SettingsNode> getChildNodes();

    /**
     * @param name the names of the sought child nodes.
     * @return a list of all child nodes of this node that have the specified name. If none are found, this should return an empty list. Never null.
     */
    public List<SettingsNode> getChildNodes(String name);

    /**
     * Returns the child node with the specified name.
     *
     * @param name the name of the sought node
     * @return the child settings node or null if no match found.
     */
    public SettingsNode getChildNode(String name);

    /**
     * Call this to add a child node to this node.
     *
     * @param name the name of the node
     * @return the child settings node.
     */
    public SettingsNode addChild(String name);

    /**
     * This adds a child node with the specified name or returns the existing child node if one already exists with said name.
     *
     * @param name the name.
     * @return the child settings node. Never null.
     */
    public SettingsNode addChildIfNotPresent(String name);

    /**
     * Returns a node at the specified path starting at 'this' node.
     *
     * @param pathPortions an array of 'names' of the nodes. The first item corresponds to a direct child of this node. The second item would be the grand child of this node. etc. etc.
     * @return a node if it exists. Null if not.
     */
    public SettingsNode getNodeAtPath(String... pathPortions);

    /**
     * Removes this node from its parent.
     */
    public void removeFromParent();

    /**
     * Deletes all the children of this node.
     */
    public void removeAllChildren();
}
