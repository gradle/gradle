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
        if (current.traversing) {
            // First child node
            current = new Node(current, node);
            if (current.isRoot()) {
                original.append(node);
                current.valueWritten = true;
            }
        } else {
            // A sibling node
            current = new Node(current.parent, node);
        }
    }

    @Override
    public void startChildren() {
        current.traversing = true;
    }

    @Override
    public void endChildren() {
        if (current.parent == null) {
            throw new IllegalStateException("Not visiting any node.");
        }
        if (!current.traversing) {
            current = current.parent;
        }
        if (current.isRoot()) {
            writeNode(current);
        }
        current = current.parent;
    }

    private void writeNode(Node node) {
        if (node.prefix == null) {
            node.prefix = node.isRoot() ? "" : node.parent.prefix + "    ";
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

    private static class Node {
        final Node parent;
        final String value;
        boolean written;
        boolean traversing;
        Node firstChild;
        Node lastChild;
        Node nextSibling;
        String prefix;
        public boolean valueWritten;

        private Node() {
            this.parent = null;
            this.value = null;
            traversing = true;
            written = true;
            prefix = "";
        }

        private Node(Node parent, String value) {
            this.parent = parent;
            this.value = value;
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

        boolean isRoot() {
            return parent.parent == null;
        }
    }
}
