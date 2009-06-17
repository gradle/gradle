/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl;

import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.SourceUnit;

import java.util.Iterator;

public class BuildScriptClasspathScriptTransformer extends AbstractScriptTransformer {
    private static final String BUILDSCRIPT_METHOD_NAME = "scriptclasspath";

    public void call(SourceUnit source) throws CompilationFailedException {

        Iterator statementIterator = source.getAST().getStatementBlock().getStatements().iterator();
        while (statementIterator.hasNext()) {
            Statement statement = (Statement) statementIterator.next();
            if (!isBuildScriptBlock(statement)) {
                statementIterator.remove();
            }
        }
    }

    private boolean isBuildScriptBlock(Statement statement) {
        if (!(statement instanceof ExpressionStatement)) {
            return false;
        }

        ExpressionStatement expressionStatement = (ExpressionStatement) statement;
        if (!(expressionStatement.getExpression() instanceof MethodCallExpression)) {
            return false;
        }

        MethodCallExpression methodCall = (MethodCallExpression) expressionStatement.getExpression();
        if (!isMethodOnThis(methodCall, BUILDSCRIPT_METHOD_NAME)) {
            return false;
        }

        if (!(methodCall.getArguments() instanceof ArgumentListExpression)) {
            return false;
        }

        ArgumentListExpression args = (ArgumentListExpression) methodCall.getArguments();
        return args.getExpressions().size() == 1 && args.getExpression(0) instanceof ClosureExpression;
    }
}


