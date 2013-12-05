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

import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.AbstractStyledTextOutput;
import org.gradle.logging.internal.LinePrefixingStyledTextOutput;
import org.gradle.util.TreeVisitor;

public class TreeFormatter extends TreeVisitor<String> {
    private final StringBuilder buffer = new StringBuilder();
    private final AbstractStyledTextOutput original;
    private String prefix = "";
    private int depth;

    public TreeFormatter() {
        original = new AbstractStyledTextOutput() {
            @Override
            protected void doAppend(String text) {
                buffer.append(text);
            }
        };
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    @Override
    public void node(String node) {
        if (depth == 0) {
            buffer.append(node);
            return;
        }

        original.format("%n");
        StyledTextOutput output = new LinePrefixingStyledTextOutput(original, prefix + "    ");
        output.append(prefix);
        output.append("  - ");
        output.append(node);
    }

    @Override
    public void startChildren() {
        depth++;
        if (depth > 1) {
            prefix = prefix + "    ";
        }
    }

    @Override
    public void endChildren() {
        depth--;
        if (depth <= 1) {
            prefix = "";
        } else {
            prefix = prefix.substring(4);
        }
    }
}
