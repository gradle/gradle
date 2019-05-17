/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.text;

import org.apache.commons.lang.StringUtils;
import org.gradle.util.TextUtil;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Constructs a tree of diagnostic messages.
 */
public class TreeFormatter implements DiagnosticsVisitor {
    private final StringBuilder buffer = new StringBuilder();
    private final AbstractStyledTextOutput original;
    private Node current;

    public TreeFormatter() {
        this.original = new AbstractStyledTextOutput() {
            @Override
            protected void doAppend(String text) {
                buffer.append(text);
            }
        };
        this.current = new Node();
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    /**
     * Starts a new node with the given text.
     */
    @Override
    public TreeFormatter node(String text) {
        if (current.state == State.TraverseChildren) {
            // First child node
            current = new Node(current, text);
        } else {
            // A sibling node
            current.state = State.Done;
            current = new Node(current.parent, text);
        }
        if (current.isTopLevelNode()) {
            // A new top level node, implicitly finish the previous node
            if (current != current.parent.firstChild) {
                // Not the first top level node
                original.append(TextUtil.getPlatformLineSeparator());
            }
            original.append(text);
            current.valueWritten = true;
        }
        return this;
    }

    /**
     * Starts a new node with the given type name.
     */
    public void node(Class<?> type) {
        // Implementation is currently dumb, can be made smarter
        node(StringUtils.capitalize(type.toString()));
    }

    /**
     * Appends text to the current node.
     */
    public void append(CharSequence text) {
        if (current.state == State.CollectValue) {
            current.value.append(text);
            if (current.valueWritten) {
                original.append(text);
            }
        } else {
            throw new IllegalStateException("Cannot append text to node.");
        }
    }

    /**
     * Appends a type name to the current node.
     */
    public void appendType(Class<?> type) {
        // Implementation is currently dumb, can be made smarter
        append(type.toString());
    }

    /**
     * Appends an annotation name to the current node.
     */
    public void appendAnnotation(Class<? extends Annotation> type) {
        append("@" + type.getSimpleName());
    }

    /**
     * Appends a method name to the current node.
     */
    public void appendMethod(Method method) {
        // Implementation is currently dumb, can be made smarter
        append(method.getDeclaringClass().getSimpleName());
        append(".");
        append(method.getName());
        append("()");
    }

    /**
     * Appends some user provided value to the current node.
     */
    public void appendValue(@Nullable Object value) {
        // Implementation is currently dumb, can be made smarter
        if (value == null) {
            append("null");
        } else if (value instanceof String) {
            append("'");
            append(value.toString());
            append("'");
        } else {
            append(value.toString());
        }
    }

    /**
     * Appends some user provided values to the current node.
     */
    public void appendValues(Object[] values) {
        // Implementation is currently dumb, can be made smarter
        append("[");
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (i > 0) {
                append(", ");
            }
            appendValue(value);
        }
        append("]");
    }

    @Override
    public TreeFormatter startChildren() {
        if (current.state == State.CollectValue) {
            current.state = State.TraverseChildren;
        } else {
            throw new IllegalStateException("Cannot start children again");
        }
        return this;
    }

    @Override
    public TreeFormatter endChildren() {
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
        return this;
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

        Separator separator = node.getFirstChildSeparator();

        if (!separator.newLine) {
            output.append(separator.text);
            Node firstChild = node.firstChild;
            output.append(firstChild.value);
            firstChild.valueWritten = true;
            firstChild.prefix = node.prefix;
            writeNode(firstChild);
        } else if (node.firstChild != null) {
            original.append(separator.text);
            writeNode(node.firstChild);
        }
        if (node.nextSibling != null) {
            original.append(TextUtil.getPlatformLineSeparator());
            writeNode(node.nextSibling);
        }
    }

    private enum State {
        CollectValue, TraverseChildren, Done
    }

    private enum Separator {
        NewLine(true, TextUtil.getPlatformLineSeparator()),
        Empty(false, " "),
        Colon(false, ": "),
        ColonNewLine(true, ":" + TextUtil.getPlatformLineSeparator());

        Separator(boolean newLine, String text) {
            this.newLine = newLine;
            this.text = text;
        }

        final boolean newLine;
        final String text;
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
            this.value = new StringBuilder();
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

        Separator getFirstChildSeparator() {
            if (firstChild == null) {
                return Separator.NewLine;
            }
            if (value.length() == 0) {
                // Always expand empty node
                return Separator.NewLine;
            }
            char trailing = value.charAt(value.length() - 1);
            if (trailing == '.') {
                // Always expand with trailing .
                return Separator.NewLine;
            }
            if (firstChild.nextSibling == null
                    && firstChild.firstChild == null
                    && value.length() + firstChild.value.length() < 60) {
                // A single leaf node as child and total text is not too long, collapse
                if (trailing == ':') {
                    return Separator.Empty;
                }
                return Separator.Colon;
            }
            // Otherwise, expand
            if (trailing == ':') {
                return Separator.NewLine;
            }
            return Separator.ColonNewLine;
        }

        boolean isTopLevelNode() {
            return parent.parent == null;
        }
    }
}
