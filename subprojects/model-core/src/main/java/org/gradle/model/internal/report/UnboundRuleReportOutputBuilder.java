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

package org.gradle.model.internal.report;

import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.io.PrintWriter;

public class UnboundRuleReportOutputBuilder {
    private final PrintWriter writer;
    private final String prefix;
    private final static String INDENT = "  ";
    private boolean first = true;

    public UnboundRuleReportOutputBuilder(PrintWriter writer, String prefix) {
        this.writer = writer;
        this.prefix = prefix;
    }

    public class Rule {

        boolean mutable;
        boolean immutable;

        private Rule bound(String path, String type) {
            item();
            writer.print("+ ");
            writer.print(String.format("%s (%s)", path, type));
            return this;
        }

        private Rule unbound(String path, String type) {
            item();
            writer.print("- ");
            String pathString = path == null ? "<unspecified>" : path;
            writer.print(String.format("%s (%s)", pathString, type));
            return this;
        }

        private void item() {
            writer.println();
            writer.print(prefix);
            writer.print(INDENT);
            writer.print(INDENT);
        }

        public Rule mutableUnbound(String path, String type) {
            mutable();
            return unbound(path, type);
        }

        public Rule mutableBound(String path, String type) {
            mutable();
            return bound(path, type);
        }

        public Rule immutableUnbound(String path, String type) {
            immutable();
            return unbound(path, type);
        }

        public Rule immutableBound(String path, String type) {
            immutable();
            return bound(path, type);
        }

        private void mutable() {
            if (immutable) {
                throw new IllegalStateException("all mutable inputs must be added before any immutable");
            }

            if (!mutable) {
                mutable = true;
                heading("Mutable:");
            }
        }

        private void immutable() {
            if (!immutable) {
                immutable = true;
                heading("Immutable:");
            }
        }

        private void heading(String heading) {
            writer.println();
            writer.print(prefix);
            writer.print(INDENT);
            writer.print(heading);
        }
    }

    public Rule rule(ModelRuleDescriptor descriptor) {
        if (!first) {
            writer.println();
        }
        first = false;
        writer.print(prefix);
        descriptor.describeTo(writer);
        return new Rule();
    }

}
