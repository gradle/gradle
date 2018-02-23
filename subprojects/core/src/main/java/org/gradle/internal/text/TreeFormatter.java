/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.text;

import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.AbstractStyledTextOutput;
import org.gradle.internal.logging.text.LinePrefixingStyledTextOutput;
import org.gradle.util.TreeVisitor;

public class TreeFormatter extends TreeVisitor<String> {
    private final StringBuilder buffer = new StringBuilder();
    private final AbstractStyledTextOutput original;
    private Node current;

    public TreeFormatter() {
        original = new AbstractStyledTextOutput() {
            @Override
            protected void doAppend(String text) {
                buffer.append(text);
            }
        };
        current = new Node();
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    @Override
    public void node(String node) {
        if (current.state == State.TraverseChildren) {
            // First child node
            current = new Node(current, node);
        } else {
            // A sibling node
            current.state = State.Done;
            current = new Node(current.parent, node);
        }
        if (current.isTopLevelNode()) {
            if (current != current.parent.firstChild) {
                // Not the first top level node
                original.format("%n");
            }
            original.append(node);
            current.valueWritten = true;
        }
    }

    public void append(CharSequence text) {
        if (current.state == State.CollectValue) {
            if (current.valueWritten) {
                original.append(text);
            } else {
                current.value.append(text);
            }
        } else {
            throw new IllegalStateException("Cannot append text to node.");
        }
    }

    @Override
    public void startChildren() {
        if (current.state == State.CollectValue) {
            current.state = State.TraverseChildren;
        } else  {
            throw new IllegalStateException("Cannot start children again");
        }
    }

    @Override
    public void endChildren() {
        if (current.parent == null) {
            throw new IllegalStateException("Not visiting any node.");
        }
        if (current.state == State.CollectValue) {
            current.state = State.Done;
            current = current.parent;
        }
        if (current.state != State.TraverseChildren) {
            throw new IllegalStateException("Cannot end children.");
        }
        if (current.isTopLevelNode()) {
            writeNode(current);
        }
        current.state = State.Done;
        current = current.parent;
    }

    private void writeNode(Node node) {
        if (node.prefix == null) {
            node.prefix = node.isTopLevelNode() ? "" : node.parent.prefix + "    ";
        }

        StyledTextOutput output = new LinePrefixingStyledTextOutput(original, node.prefix, false);
        if (!node.valueWritten) {
            output.append(node.parent.prefix);
            output.append("  - ");
            output.append(node.value);
        }

        if (node.canCollapseFirstChild()) {
            output.append(": ");
            Node firstChild = node.firstChild;
            output.append(firstChild.value);
            firstChild.valueWritten = true;
            firstChild.prefix = node.prefix;
            writeNode(firstChild);
        } else if (node.firstChild != null) {
            original.format(":%n");
            writeNode(node.firstChild);
        }
        if (node.nextSibling != null) {
            original.format("%n");
            writeNode(node.nextSibling);
        }
    }

    private enum State {
        CollectValue, TraverseChildren, Done
    }

    private static class Node {
        final Node parent;
        final StringBuilder value;
        Node firstChild;
        Node lastChild;
        Node nextSibling;
        String prefix;
        State state;
        boolean valueWritten;

        private Node() {
            this.parent = null;
            this.value = null;
            prefix = "";
            state = State.TraverseChildren;
        }

        private Node(Node parent, String value) {
            this.parent = parent;
            this.value = new StringBuilder(value);
            state = State.CollectValue;
            if (parent.firstChild == null) {
                parent.firstChild = this;
                parent.lastChild = this;
            } else {
                parent.lastChild.nextSibling = this;
                parent.lastChild = this;
            }
        }

        boolean canCollapseFirstChild() {
            return firstChild != null && firstChild.nextSibling == null && !firstChild.canCollapseFirstChild();
        }

        boolean isTopLevelNode() {
            return parent.parent == null;
        }
    }
}
