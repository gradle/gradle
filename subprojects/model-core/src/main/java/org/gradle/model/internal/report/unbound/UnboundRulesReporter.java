/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.report.unbound;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import net.jcip.annotations.NotThreadSafe;

import java.io.PrintWriter;
import java.util.List;

@NotThreadSafe
public class UnboundRulesReporter {

    private final PrintWriter writer;
    private final String prefix;
    private final static String INDENT = "  ";

    public UnboundRulesReporter(PrintWriter writer, String prefix) {
        this.writer = writer;
        this.prefix = prefix;
    }

    public void reportOn(Iterable<? extends UnboundRule> rules) {
        boolean first = true;
        for (UnboundRule rule : rules) {
            if (!first) {
                writer.println();
            }
            first = false;

            writer.print(prefix);

            writer.print(rule.getDescriptor());
            if (rule.getMutableInputs().size() > 0) {
                heading("Subject:");
                reportInputs(rule.getMutableInputs());
            }
            if (rule.getImmutableInputs().size() > 0) {
                heading("Inputs:");
                reportInputs(rule.getImmutableInputs());
            }
        }
    }

    private void reportInputs(Iterable<? extends UnboundRuleInput> inputs) {
        for (UnboundRuleInput input : inputs) {
            item();
            List<String> parts = Lists.newArrayList();
            parts.add("");
            parts.add(String.format("Found:%s", input.isBound()));

            String path = input.getPath() == null ? "<unspecified>" : input.getPath();
            parts.add("Path:" + path);
            parts.add("Type:" + input.getType());
            if (input.getDescription() != null) {
                parts.add("Description:" + input.getDescription());
            }
            if (input.getPath() == null && input.getScope() != null) {
                parts.add(String.format("Scope:'%s'", input.getScope()));
            }
            if (input.getSuggestedPaths().size() > 0) {
                String join = Joiner.on(", ").join(input.getSuggestedPaths());
                parts.add(String.format("Suggestions:%s", join.trim()));
            }
            writer.print(Joiner.on(" | ").join(parts) + "|");
        }
    }

    private void item() {
        writer.println();
        writer.print(prefix);
        writer.print(INDENT);
        writer.print(INDENT);
    }

    private void heading(String heading) {
        writer.println();
        writer.print(prefix);
        writer.print(INDENT);
        writer.print(heading);
    }
}
