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

package org.gradle.model.dsl.internal.transform;

import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;

import javax.annotation.Nullable;
import java.net.URI;

public class SourceLocation {
    private final @Nullable URI uri;
    private final String scriptSourceDescription;
    private final String expression;
    private final int lineNumber;
    private final int columnNumber;

    public SourceLocation(@Nullable URI uri, String scriptSourceDescription, String expression, int lineNumber, int columnNumber) {
        this.uri = uri;
        this.scriptSourceDescription = scriptSourceDescription;
        this.expression = expression;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    /**
     * Called from generated code. See {@link RuleVisitor#visitGeneratedClosure(org.codehaus.groovy.ast.ClassNode)}
     */
    @SuppressWarnings("unused")
    public SourceLocation(@Nullable String uri, String scriptSourceDescription, String expression, int lineNumber, int columnNumber) {
        this(uri == null ? null : URI.create(uri), scriptSourceDescription, expression, lineNumber, columnNumber);
    }

    public String getExpression() {
        return expression;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    @Nullable
    public URI getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return scriptSourceDescription + " line " + lineNumber + ", column " + columnNumber;
    }

    public ModelRuleDescriptor asDescriptor() {
        return new SimpleModelRuleDescriptor(expression + " @ " + toString());
    }
}
