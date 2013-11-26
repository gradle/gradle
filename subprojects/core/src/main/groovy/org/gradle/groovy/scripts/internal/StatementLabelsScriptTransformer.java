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

import com.google.common.collect.Lists;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;

import java.util.List;

public class StatementLabelsScriptTransformer extends AbstractScriptTransformer {
    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    public String getId() {
        return "labels";
    }

    @Override
    public void call(final SourceUnit source) throws CompilationFailedException {
        final List<Statement> logStats = Lists.newArrayList();

        // currently we only look in script code; could extend this to build script classes
        AstUtils.visitScriptCode(source, new ClassCodeVisitorSupport() {
            @Override
            protected SourceUnit getSourceUnit() {
                return source;
            }

            @Override
            protected void visitStatement(Statement statement) {
                if (statement.getStatementLabel() != null) {
                    // Because we aren't failing the build, the script will be cached and this transformer won't run the next time.
                    // In order to make the deprecation warning stick, we have to weave the call to StatementLabelsDeprecationLogger
                    // into the build script.
                    String label = statement.getStatementLabel();
                    String sample = source.getSample(statement.getLineNumber(), statement.getColumnNumber(), null);
                    Expression logExpr = new StaticMethodCallExpression(ClassHelper.makeWithoutCaching(StatementLabelsDeprecationLogger.class), "log",
                            new ArgumentListExpression(new ConstantExpression(label), new ConstantExpression(sample)));
                    logStats.add(new ExpressionStatement(logExpr));
                }
            }
        });

        source.getAST().getStatementBlock().addStatements(logStats);
    }
}
