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
package org.gradle.foundation;

import junit.framework.TestCase;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.gradle.gradleplugin.foundation.Dom4JUtility;
import org.gradle.gradleplugin.foundation.settings.DOM4JSettingsNode;
import org.gradle.gradleplugin.foundation.settings.SettingsNode;

import java.util.List;

/**
 * Tests the DOM4JSettingsNode class.
 */
public class DOM4JSettingsNodeTest extends TestCase {
    private DOM4JSettingsNode rootNode;
    private Element rootElement;

    private static final String SAMPLE_NAME_1 = "fred";
    private static final String SAMPLE_NAME_2 = "jack";
    private static final String SAMPLE_NAME_3 = "bob";

    @Override
    protected void setUp() throws Exception {
        Document document = DocumentHelper.createDocument();
        rootElement = document.addElement("root");
        rootNode = new DOM4JSettingsNode(rootElement);
    }

    /**
     * This tests that addChild actually works. We'll verify a child isn't present, then add one.
     */
    public void testAddChild() {
        //make sure we have no child named 'fred' at the DOM4J level
        assertNull(rootElement.element(SAMPLE_NAME_1));

        //as such, we shouldn't have one at the DOM4JSettingsNode level either.
        assertNull(rootNode.getChildNode(SAMPLE_NAME_1));

        SettingsNode settingsNode = rootNode.addChild(SAMPLE_NAME_1);
        assertNotNull(settingsNode);

        //and now it should be present under both.
        assertNotNull(Dom4JUtility.getChild(rootElement, DOM4JSettingsNode.TAG_NAME, DOM4JSettingsNode.NAME_ATTRIBUTE,
                SAMPLE_NAME_1));
        assertNotNull(rootNode.getChildNode(SAMPLE_NAME_1));
    }

    /**
     * This tests that if you call addChildIfNotPresent, it actually adds a child that is not present. We'll verify a
     * child isn't present, then add one.
     */
    public void testAddingChildrenIfNotPresent() {
        //make sure we have no child named 'fred' at the DOM4J level
        assertNull(rootElement.element(SAMPLE_NAME_1));

        //as such, we shouldn't have one at the DOM4JSettingsNode level either.
        assertNull(rootNode.getChildNode(SAMPLE_NAME_1));

        SettingsNode settingsNode = rootNode.addChildIfNotPresent(SAMPLE_NAME_1);
        assertNotNull(settingsNode);

        //and now it should be present under both.
        assertNotNull(Dom4JUtility.getChild(rootElement, DOM4JSettingsNode.TAG_NAME, DOM4JSettingsNode.NAME_ATTRIBUTE,
                SAMPLE_NAME_1));
        assertNotNull(rootNode.getChildNode(SAMPLE_NAME_1));
    }

    /**
     * This tests that if you call addChildIfNotPresent, it won't add a child if one is already present. We'll verify a
     * child is present, then call addChildIfNotPresent.
     */
    public void testAddingChildrenIfNotPresent2() {
        rootNode.addChild(SAMPLE_NAME_1);

        assertNotNull(Dom4JUtility.getChild(rootElement, DOM4JSettingsNode.TAG_NAME, DOM4JSettingsNode.NAME_ATTRIBUTE,
                SAMPLE_NAME_1));
        assertNotNull(rootNode.getChildNode(SAMPLE_NAME_1));

        List list = Dom4JUtility.getChildren(rootElement, DOM4JSettingsNode.TAG_NAME, DOM4JSettingsNode.NAME_ATTRIBUTE,
                SAMPLE_NAME_1);
        assertEquals(1, list.size());  //there should only be the one element.

        SettingsNode settingsNode = rootNode.addChildIfNotPresent(SAMPLE_NAME_1);
        assertNotNull(settingsNode);

        //it should still be present under both.
        assertNotNull(Dom4JUtility.getChildren(rootElement, DOM4JSettingsNode.TAG_NAME,
                DOM4JSettingsNode.NAME_ATTRIBUTE, SAMPLE_NAME_1));
        assertNotNull(rootNode.getChildNode(SAMPLE_NAME_1));

        //but make sure we didn't add an additional one. There should still be only one element.
        list = Dom4JUtility.getChildren(rootElement, DOM4JSettingsNode.TAG_NAME, DOM4JSettingsNode.NAME_ATTRIBUTE,
                SAMPLE_NAME_1);
        assertEquals(1, list.size());
    }

    /**
     * This tests that getChildNode works. We'll add some nodes and make sure they are found correctly. We'll also add a
     * duplicate named node. It should never be returned because getChildNode only finds the first one. Lastly, we'll
     * call getChildNode for a node that doesn't exist. It shouldn't be returned.
     */
    public void testGetChildNode() {
        SettingsNode childNode1 = rootNode.addChild(SAMPLE_NAME_1);
        SettingsNode childNode2 = rootNode.addChild(SAMPLE_NAME_2);
        SettingsNode childNode3 = rootNode.addChild(SAMPLE_NAME_3);
        SettingsNode childNode4 = rootNode.addChild(
                SAMPLE_NAME_2);  //this is a duplicate and should never be found via getChildNode.

        assertNotNull(Dom4JUtility.getChild(rootElement, DOM4JSettingsNode.TAG_NAME, DOM4JSettingsNode.NAME_ATTRIBUTE,
                SAMPLE_NAME_1));
        assertNotNull(Dom4JUtility.getChild(rootElement, DOM4JSettingsNode.TAG_NAME, DOM4JSettingsNode.NAME_ATTRIBUTE,
                SAMPLE_NAME_2));
        assertNotNull(Dom4JUtility.getChild(rootElement, DOM4JSettingsNode.TAG_NAME, DOM4JSettingsNode.NAME_ATTRIBUTE,
                SAMPLE_NAME_3));
        assertNotNull(rootNode.getChildNode(SAMPLE_NAME_1));
        assertNotNull(rootNode.getChildNode(SAMPLE_NAME_2));
        assertNotNull(rootNode.getChildNode(SAMPLE_NAME_3));

        SettingsNode foundNode1 = rootNode.getChildNode(SAMPLE_NAME_1);
        assertEquals(foundNode1, childNode1);

        SettingsNode foundNode2 = rootNode.getChildNode(SAMPLE_NAME_2);
        assertEquals(foundNode2, childNode2);

        SettingsNode foundNode3 = rootNode.getChildNode(SAMPLE_NAME_3);
        assertEquals(foundNode3, childNode3);

        //look for the duplicated
        SettingsNode foundNode2B = rootNode.getChildNode(SAMPLE_NAME_2);
        assertEquals(foundNode2B, childNode2);  //should still be childNode2.

        //this one shouldn't be found.
        SettingsNode foundNode4 = rootNode.getChildNode("notpresent");
        assertNull(foundNode4);
    }

    /**
     * This tests getChildNodes. We'll make sure it returns all the child nodes. First we'll make sure it returns
     * nothing when no children are present. Then we'll add some nodes, and make sure it returns them all. Lastly, we'll
     * add some more again to make sure they're included in the list.
     */
    public void testGetChildNodes() {
        //try it with no nodes
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //add some test nodes
        SettingsNode childNode1 = rootNode.addChild(SAMPLE_NAME_1);
        SettingsNode childNode2 = rootNode.addChild(SAMPLE_NAME_2);
        SettingsNode childNode3 = rootNode.addChild(SAMPLE_NAME_3);
        SettingsNode childNode4 = rootNode.addChild(SAMPLE_NAME_2);  //this is a duplicate of childNode2

        //all should be returned
        children = rootNode.getChildNodes();
        TestUtility.assertListContents(children, childNode1, childNode2, childNode3, childNode4);

        //add some more nodes
        SettingsNode childNode5 = rootNode.addChild(SAMPLE_NAME_1);
        SettingsNode childNode6 = rootNode.addChild(SAMPLE_NAME_2);

        //again, all should be returned
        children = rootNode.getChildNodes();
        TestUtility.assertListContents(children, childNode1, childNode2, childNode3, childNode4, childNode5,
                childNode6);
    }

    /**
     * This tests getChildNodes that takes a sought node name. We'll make sure it returns all the child nodes. First
     * we'll make sure it returns nothing when no children are present. Then we'll add some nodes and make sure it
     * returns the correct ones. Lastly, we'll add some more again to make sure they're appropriately included in the
     * list.
     */
    public void testGetChildNodesByName() {
        //try it with no nodes
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //add some test nodes
        SettingsNode childNode1 = rootNode.addChild(SAMPLE_NAME_1);
        SettingsNode childNode2 = rootNode.addChild(SAMPLE_NAME_2);
        SettingsNode childNode3 = rootNode.addChild(SAMPLE_NAME_3);
        SettingsNode childNode4 = rootNode.addChild(SAMPLE_NAME_2);  //this is a duplicate of childNode2

        //only 2 and 4 match
        children = rootNode.getChildNodes(SAMPLE_NAME_2);
        TestUtility.assertListContents(children, childNode2, childNode4);

        //add some more nodes. Only 1 is a match
        SettingsNode childNode5 = rootNode.addChild(SAMPLE_NAME_1);
        SettingsNode childNode6 = rootNode.addChild(SAMPLE_NAME_2);

        //node 6 should also returned now
        children = rootNode.getChildNodes(SAMPLE_NAME_2);
        TestUtility.assertListContents(children, childNode2, childNode4, childNode6);
    }

    /**
     * This verifies that getName and setName work correctly. We call each and make sure the value is stored and
     * retrieved correctly.
     */
    public void testName() {
        SettingsNode childNode1 = rootNode.addChild(SAMPLE_NAME_1);

        assertEquals(SAMPLE_NAME_1, childNode1.getName());

        childNode1.setName(SAMPLE_NAME_3);

        assertEquals(SAMPLE_NAME_3, childNode1.getName());
    }

    /**
     * This verifies that getValue and setValue work correctly. We call each and make sure the value is stored and
     * retrieved correctly.
     */
    public void testValue() {
        SettingsNode childNode1 = rootNode.addChild(SAMPLE_NAME_1);

        assertNull(childNode1.getValue());

        childNode1.setValue("myvalue");

        assertEquals("myvalue", childNode1.getValue());

        childNode1.setValue("someothervalue");

        assertEquals("someothervalue", childNode1.getValue());
    }

    /**
     * This tests that removeFromParent works. We'll add some sample nodes and then delete them one by one and verify
     * they're really removed. We'll also add two with the same name just to make sure that the wrong one isn't
     * deleted.
     */
    public void testRemoveFromParent() {
        //make sure we have no children first
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //add some sample nodes.
        SettingsNode childNode1 = rootNode.addChild(SAMPLE_NAME_1);
        SettingsNode childNode2 = rootNode.addChild(SAMPLE_NAME_2);
        SettingsNode childNode3 = rootNode.addChild(SAMPLE_NAME_3);
        SettingsNode childNode4 = rootNode.addChild(SAMPLE_NAME_2);  //notice this has the same name as childNode2

        //make sure they're all present as expected
        children = rootNode.getChildNodes();
        TestUtility.assertListContents(children, childNode1, childNode2, childNode3, childNode4);

        //now give the two with the same names different values
        childNode2.setValue("first");
        childNode4.setValue("second");

        //delete the 'first' one with SAMPLE_NAME_2
        childNode2.removeFromParent();

        //make sure its not longer present
        children = rootNode.getChildNodes();
        TestUtility.assertListContents(children, childNode1, childNode3, childNode4);

        //make sure that we didn't delete the wrong node with SAMPLE_NAME_2. The 'second' one should still be present.
        SettingsNode foundNode = rootNode.getChildNode(SAMPLE_NAME_2);
        assertEquals("second", foundNode.getValue());

        //delete another one and make sure its no longer present
        childNode3.removeFromParent();
        children = rootNode.getChildNodes();
        TestUtility.assertListContents(children, childNode1, childNode4);

        //delete yet another one and make sure its no longer present
        childNode1.removeFromParent();
        children = rootNode.getChildNodes();
        TestUtility.assertListContents(children, childNode4);

        //delete the last one and make sure that the children are now empty.
        childNode4.removeFromParent();
        children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //just for grins, try to delete one that's already deleted. It shouldn't do anything (like crash)
        childNode3.removeFromParent();
        children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //just to be paranoid, verify they're gone using getChildNode. Each of these should return nothing.
        assertNull(rootNode.getChildNode(SAMPLE_NAME_1));
        assertNull(rootNode.getChildNode(SAMPLE_NAME_2));
        assertNull(rootNode.getChildNode(SAMPLE_NAME_3));

        //make sure the nodes are gone from a DOM4J standpoint.
        assertEquals(0, rootElement.elements().size());
    }

    /**
     * This tests removeAllChildren. We'll add some nodes and call removeAllChildren and then make sure they're no
     * longer present.
     */
    public void testRemoveAllChildren() {
        //make sure we have no children first
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //add some sample nodes.
        SettingsNode childNode1 = rootNode.addChild(SAMPLE_NAME_1);
        SettingsNode childNode2 = rootNode.addChild(SAMPLE_NAME_2);
        SettingsNode childNode3 = rootNode.addChild(SAMPLE_NAME_3);
        SettingsNode childNode4 = rootNode.addChild(SAMPLE_NAME_2);

        //make sure they're all present as expected
        children = rootNode.getChildNodes();
        TestUtility.assertListContents(children, childNode1, childNode2, childNode3, childNode4);

        //now remove all children
        rootNode.removeAllChildren();

        //and make sure they're gone
        children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //just to be paranoid, verify they're gone using getChildNode. Each of these should return nothing.
        assertNull(rootNode.getChildNode(SAMPLE_NAME_1));
        assertNull(rootNode.getChildNode(SAMPLE_NAME_2));
        assertNull(rootNode.getChildNode(SAMPLE_NAME_3));

        //make sure the nodes are gone from a DOM4J standpoint.
        assertEquals(0, rootElement.elements().size());
    }

    /**
     * This tests getNodeAtPath. We want to make sure it locates nodes via path from several locations.
     */
    public void testGetNodeAtPath() {
        //add some sample nodes. I'm indenting these to better show the structure I'm making
        SettingsNode childNode1 = rootNode.addChild(SAMPLE_NAME_1);
        SettingsNode grandChildNodeA1 = childNode1.addChild("sub_nodeA1");
        SettingsNode greatGrandChildNodeA11 = grandChildNodeA1.addChild("sub_sub_nodeA11");
        SettingsNode greatGrandChildNodeA12 = grandChildNodeA1.addChild("sub_sub_nodeA12");
        SettingsNode grandChildNodeA2 = childNode1.addChild("sub_nodeA2");
        SettingsNode greatGrandChildNodeA21 = grandChildNodeA2.addChild("sub_sub_nodeA21");
        SettingsNode greatGrandChildNodeA22 = grandChildNodeA2.addChild("sub_sub_nodeA22");

        SettingsNode childNode2 = rootNode.addChild(SAMPLE_NAME_2);
        SettingsNode grandChildNodeB1 = childNode2.addChild("sub_nodeB1");
        SettingsNode greatGrandChildNodeB11 = grandChildNodeB1.addChild("sub_sub_nodeB11");
        SettingsNode greatGrandChildNodeB12 = grandChildNodeB1.addChild("sub_sub_nodeB12");
        SettingsNode grandChildNodeB2 = childNode2.addChild("sub_nodeB2");

        SettingsNode childNode3 = rootNode.addChild(SAMPLE_NAME_3);

        //now start searching for some nodes
        SettingsNode foundNode1 = rootNode.getNodeAtPath(SAMPLE_NAME_1, "sub_nodeA2", "sub_sub_nodeA22");
        assertEquals(greatGrandChildNodeA22, foundNode1);

        //try searching from something other than the root. It's still relative to the starting node.
        SettingsNode foundNode2 = childNode2.getNodeAtPath("sub_nodeB1", "sub_sub_nodeB11");
        assertEquals(greatGrandChildNodeB11, foundNode2);

        //try searching for something that doesn't exist at the first level of the sought path.
        SettingsNode foundNode3 = rootNode.getNodeAtPath("nonexistent", "sub_nodeA2", "sub_sub_nodeA22");
        assertNull(foundNode3);

        //try searching for something that doesn't exist at the second level of the sought path
        SettingsNode foundNode4 = rootNode.getNodeAtPath(SAMPLE_NAME_3, "sub_nodeA2", "sub_sub_nodeA22");
        assertNull(foundNode4);

        //try searching for something that doesn't exist at the last level of the sought path
        SettingsNode foundNode5 = rootNode.getNodeAtPath(SAMPLE_NAME_2, "sub_nodeB2", "sub_sub_nodeB22");
        assertNull(foundNode5);

        //try searching for a node using a single path
        SettingsNode foundNode6 = rootNode.getNodeAtPath(SAMPLE_NAME_3);
        assertEquals(childNode3, foundNode6);
    }

    /**
     * This tests setValueOfChild. We're going to make sure the value is set as well as that repeated calls to this
     * don't add child nodes.
     */
    public void testSetValueOfChild() {
        //make sure we have no children first
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //set the value of a child
        rootNode.setValueOfChild(SAMPLE_NAME_1, "myValue");

        //verify it was set properly
        SettingsNode childNode1 = rootNode.getChildNode(SAMPLE_NAME_1);
        assertNotNull(childNode1);
        assertEquals("myValue", childNode1.getValue());

        //make sure there's only 1 child.
        children = rootNode.getChildNodes();
        assertEquals(1, children.size());

        //set the value again. This should set the value and NOT add an additional node
        rootNode.setValueOfChild(SAMPLE_NAME_1, "newvalue");
        childNode1 = rootNode.getChildNode(SAMPLE_NAME_1);
        assertNotNull(childNode1);
        assertEquals("newvalue", childNode1.getValue());

        //make sure there's still only 1 child.
        children = rootNode.getChildNodes();
        assertEquals(1, children.size());
    }

    /**
     * This tests getValueOfChild. We're interested in that it gets the child value correctly, but also that it returns
     * the default value if either the node or its value isn't present.
     */
    public void testGetValueOfChild() {
        //make sure we have no children first
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //set the value of a child
        rootNode.setValueOfChild(SAMPLE_NAME_1, "myValue");
        rootNode.setValueOfChild(SAMPLE_NAME_2, "otherValue");
        rootNode.setValueOfChild(SAMPLE_NAME_3, "lastValue");

        assertEquals("otherValue", rootNode.getValueOfChild(SAMPLE_NAME_2, "default2"));
        assertEquals("myValue", rootNode.getValueOfChild(SAMPLE_NAME_1, "default1"));
        assertEquals("lastValue", rootNode.getValueOfChild(SAMPLE_NAME_3, "default3"));

        //now try it with one that doesn't exist. We should get the default value
        assertEquals("default4", rootNode.getValueOfChild("nonexistent", "default4"));

        //now add a single node but don't give it a value (which means its null)
        SettingsNode lastNode = rootNode.addChild("valueless");
        assertNull(lastNode.getValue());

        //now try to get its value. We should get the default value
        assertEquals("default5", rootNode.getValueOfChild("valueless", "default5"));
    }

    /**
     * This tests setValueOfChildAsInt. We're going to make sure the value is set as well as that repeated calls to this
     * don't add child nodes.
     */
    public void testSetValueOfChildAsInt() {
        //make sure we have no children first
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //set the value of a child
        rootNode.setValueOfChildAsInt(SAMPLE_NAME_1, 8);

        //verify it was set properly
        SettingsNode childNode1 = rootNode.getChildNode(SAMPLE_NAME_1);
        assertNotNull(childNode1);
        assertEquals("8", childNode1.getValue());

        //make sure there's only 1 child.
        children = rootNode.getChildNodes();
        assertEquals(1, children.size());

        //set the value again. This should set the value and NOT add an additional node
        rootNode.setValueOfChildAsInt(SAMPLE_NAME_1, 39);
        childNode1 = rootNode.getChildNode(SAMPLE_NAME_1);
        assertNotNull(childNode1);
        assertEquals("39", childNode1.getValue());

        //make sure there's still only 1 child.
        children = rootNode.getChildNodes();
        assertEquals(1, children.size());
    }

    /**
     * This tests getValueOfChildAsInt. We're interested in that it gets the child value correctly, but also that it
     * returns the default value if either the node, its value isn't present, or the valid isn't illegal as an int.
     */
    public void testGetValueOfChildAsInt() {
        //make sure we have no children first
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //set the value of a child
        rootNode.setValueOfChild(SAMPLE_NAME_1, "1");
        rootNode.setValueOfChild(SAMPLE_NAME_2, "84");
        rootNode.setValueOfChild(SAMPLE_NAME_3, "0983");

        assertEquals(84, rootNode.getValueOfChildAsInt(SAMPLE_NAME_2, 55));
        assertEquals(1, rootNode.getValueOfChildAsInt(SAMPLE_NAME_1, 66));
        assertEquals(983, rootNode.getValueOfChildAsInt(SAMPLE_NAME_3, 77));

        //now try it with one that doesn't exist. We should get the default value
        assertEquals(44, rootNode.getValueOfChildAsInt("nonexistent", 44));

        //now add a single node but don't give it a value (which means its null)
        SettingsNode valuelessNode = rootNode.addChild("valueless");
        assertNull(valuelessNode.getValue());

        //now try to get its value. We should get the default value
        assertEquals(17, rootNode.getValueOfChildAsInt("valueless", 17));

        //now add a single node that has an illegal value
        SettingsNode illegalNode = rootNode.addChild("illegal");
        illegalNode.setValue("abcdefg");

        //now try to get its value. We should get the default value
        assertEquals(333, rootNode.getValueOfChildAsInt("illegal", 333));
    }

    /**
     * This tests setValueOfChildAsLong. We're going to make sure the value is set as well as that repeated calls to
     * this don't add child nodes.
     */
    public void testSetValueOfChildAsLong() {
        //make sure we have no children first
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //set the value of a child
        rootNode.setValueOfChildAsLong(SAMPLE_NAME_1, 8000000000l);

        //verify it was set properly
        SettingsNode childNode1 = rootNode.getChildNode(SAMPLE_NAME_1);
        assertNotNull(childNode1);
        assertEquals("8000000000", childNode1.getValue());

        //make sure there's only 1 child.
        children = rootNode.getChildNodes();
        assertEquals(1, children.size());

        //set the value again. This should set the value and NOT add an additional node
        rootNode.setValueOfChildAsLong(SAMPLE_NAME_1, 3900000000l);
        childNode1 = rootNode.getChildNode(SAMPLE_NAME_1);
        assertNotNull(childNode1);
        assertEquals("3900000000", childNode1.getValue());

        //make sure there's still only 1 child.
        children = rootNode.getChildNodes();
        assertEquals(1, children.size());
    }

    /**
     * This tests getValueOfChildAsLong. We're interested in that it gets the child value correctly, but also that it
     * returns the default value if either the node, its value isn't present, or the valid isn't illegal as an long.
     */
    public void testGetValueOfChildAsLong() {
        //make sure we have no children first
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //set the value of a child
        rootNode.setValueOfChild(SAMPLE_NAME_1, "1000000000");
        rootNode.setValueOfChild(SAMPLE_NAME_2, "8400000000");
        rootNode.setValueOfChild(SAMPLE_NAME_3, "0983000000");

        assertEquals(8400000000l, rootNode.getValueOfChildAsLong(SAMPLE_NAME_2, 5500000000l));
        assertEquals(1000000000l, rootNode.getValueOfChildAsLong(SAMPLE_NAME_1, 6600000000l));
        assertEquals(983000000l, rootNode.getValueOfChildAsLong(SAMPLE_NAME_3, 7700000000l));

        //now try it with one that doesn't exist. We should get the default value
        assertEquals(4400000000l, rootNode.getValueOfChildAsLong("nonexistent", 4400000000l));

        //now add a single node but don't give it a value (which means its null)
        SettingsNode valuelessNode = rootNode.addChild("valueless");
        assertNull(valuelessNode.getValue());

        //now try to get its value. We should get the default value
        assertEquals(1700000000l, rootNode.getValueOfChildAsLong("valueless", 1700000000l));

        //now add a single node that has an illegal value
        SettingsNode illegalNode = rootNode.addChild("illegal");
        illegalNode.setValue("abcdefg");

        //now try to get its value. We should get the default value
        assertEquals(33300000000l, rootNode.getValueOfChildAsLong("illegal", 33300000000l));
    }

    /**
     * This tests setValueOfChildAsBoolean. We're going to make sure the value is set as well as that repeated calls to
     * this don't add child nodes.
     */
    public void testSetValueOfChildAsBoolean() {
        //make sure we have no children first
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //set the value of a child
        rootNode.setValueOfChildAsBoolean(SAMPLE_NAME_1, true);

        //verify it was set properly
        SettingsNode childNode1 = rootNode.getChildNode(SAMPLE_NAME_1);
        assertNotNull(childNode1);
        assertEquals("true", childNode1.getValue());

        //make sure there's only 1 child.
        children = rootNode.getChildNodes();
        assertEquals(1, children.size());

        //set the value again. This should set the value and NOT add an additional node
        rootNode.setValueOfChildAsBoolean(SAMPLE_NAME_1, false);
        childNode1 = rootNode.getChildNode(SAMPLE_NAME_1);
        assertNotNull(childNode1);
        assertEquals("false", childNode1.getValue());

        //make sure there's still only 1 child.
        children = rootNode.getChildNodes();
        assertEquals(1, children.size());

        //set the value again to the same value. Again, this should set the value and NOT add an additional node
        rootNode.setValueOfChildAsBoolean(SAMPLE_NAME_1, false);
        childNode1 = rootNode.getChildNode(SAMPLE_NAME_1);
        assertNotNull(childNode1);
        assertEquals("false", childNode1.getValue());

        //make sure there's still only 1 child.
        children = rootNode.getChildNodes();
        assertEquals(1, children.size());
    }

    /**
     * This tests getValueOfChildAsBoolean. We're interested in that it gets the child value correctly, but also that it
     * returns the default value if either the node, its value isn't present, or the valid isn't illegal as a boolean.
     * Because we're dealing with just true and false, I'll test several of these twice; once with a default of true and
     * again with a default of false. This is just to be paranoid.
     */
    public void testGetValueOfChildAsBoolean() {
        //make sure we have no children first
        List<SettingsNode> children = rootNode.getChildNodes();
        assertEquals(0, children.size());

        //set the value of a child
        rootNode.setValueOfChild(SAMPLE_NAME_1, "true");
        rootNode.setValueOfChild(SAMPLE_NAME_2, "false");
        rootNode.setValueOfChild(SAMPLE_NAME_3, "true");

        assertEquals(false, rootNode.getValueOfChildAsBoolean(SAMPLE_NAME_2, true));
        assertEquals(true, rootNode.getValueOfChildAsBoolean(SAMPLE_NAME_1, false));
        assertEquals(true, rootNode.getValueOfChildAsBoolean(SAMPLE_NAME_3, false));

        //now try it with one that doesn't exist. We should get the default value
        assertEquals(true, rootNode.getValueOfChildAsBoolean("nonexistent", true));

        //see header
        assertEquals(false, rootNode.getValueOfChildAsBoolean("nonexistent2", false));

        //now add a single node but don't give it a value (which means its null)
        SettingsNode valuelessNode = rootNode.addChild("valueless");
        assertNull(valuelessNode.getValue());

        //now try to get its value. We should get the default value
        assertEquals(true, rootNode.getValueOfChildAsBoolean("valueless", true));

        //see header
        assertEquals(false, rootNode.getValueOfChildAsBoolean("valueless", false));

        //now add a single node that has an illegal value
        SettingsNode illegalNode = rootNode.addChild("illegal");
        illegalNode.setValue("abcdefg");

        //now try to get its value. We should get the default value
        assertEquals(true, rootNode.getValueOfChildAsBoolean("illegal", true));

        //see header
        assertEquals(false, rootNode.getValueOfChildAsBoolean("illegal", false));
    }
}
