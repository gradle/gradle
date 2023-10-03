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
import javax.annotation.concurrent.NotThreadSafe;

import java.io.PrintWriter;

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
        for (UnboundRule rule : rules) {
            writer.print(prefix);
            writer.println(rule.getDescriptor());
            if (rule.getMutableInputs().size() > 0) {
                heading("subject:");
                reportInputs(rule.getMutableInputs());
            }
            if (rule.getImmutableInputs().size() > 0) {
                heading("inputs:");
                reportInputs(rule.getImmutableInputs());
            }
            writer.println();
        }
        writer.println("[*] - indicates that a model item could not be found for the path or type.");
    }

    private void reportInputs(Iterable<? extends UnboundRuleInput> inputs) {
        for (UnboundRuleInput input : inputs) {
            writer.print(indent(2));
            writer.write("- ");
            writer.write(input.getPath() == null ? "<no path>" : input.getPath());
            writer.write(" ");
            writer.write(input.getType() == null ? "<untyped>" : input.getType());
            if (input.getDescription() != null) {
                writer.write(" ");
                writer.write("(");
                writer.write(input.getDescription());
                writer.write(")");
            }
            if (!input.isBound()) {
                writer.write(" ");
                writer.write("[*]");
            }
            writer.println();
            if (input.getPath() == null && input.getScope() != null) {
                writer.write(indent(4));
                writer.write("scope: ");
                writer.println(input.getScope());
            }
            if (input.getSuggestedPaths().size() > 0) {
                writer.write(indent(4));
                writer.write("suggestions: ");
                writer.println(Joiner.on(", ").join(input.getSuggestedPaths()));
            }
        }
    }

    private void heading(String heading) {
        writer.print(indent(1));
        writer.println(heading);
    }

    private String indent(int times) {
        StringBuffer buff = new StringBuffer(prefix);
        for (int i = 0; i < times; i++) {
            buff.append(INDENT);
        }
        return buff.toString();
    }
}
