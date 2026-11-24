/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;

public class StatementLabelsScriptTransformer extends AbstractScriptTransformer {
    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    @Override
    public void call(final SourceUnit source) throws CompilationFailedException {
        // currently we only look in script code; could extend this to build script classes
        AstUtils.visitScriptCode(source, new ClassCodeVisitorSupport() {
            @Override
            protected SourceUnit getSourceUnit() {
                return source;
            }

            @Override
            protected void visitStatement(Statement statement) {
                if (statement.getStatementLabels() != null && !statement.getStatementLabels().isEmpty()) {
                    String message = String.format("Statement labels may not be used in build scripts.%nIn case you tried to configure a property named '%s', replace ':' with '=' or ' ', otherwise it will not have the desired effect.",
                            statement.getStatementLabels().get(0));
                    addError(message, statement);
                }
            }
        });
    }
}
