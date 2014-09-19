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

import java.io.PrintWriter;

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
                heading("Mutable:");
                reportInputs(rule.getMutableInputs());
            }
            if (rule.getImmutableInputs().size() > 0) {
                heading("Immutable:");
                reportInputs(rule.getImmutableInputs());
            }
        }
    }

    private void reportInputs(Iterable<? extends UnboundRuleInput> inputs) {
        for (UnboundRuleInput input : inputs) {
            item();
            writer.print(input.isBound() ? "+ " : "- ");
            String path = input.getPath() == null ? "<unspecified>" : input.getPath();
            writer.print(String.format("%s (%s)", path, input.getType()));
            if (input.getDescription() != null) {
                writer.print(String.format(" %s", input.getDescription()));
            }
            if (input.getSuggestedPaths().size() > 0) {
                writer.print(" - suggestions: ");
                writer.print(Joiner.on(", ").join(input.getSuggestedPaths()));
            }
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
