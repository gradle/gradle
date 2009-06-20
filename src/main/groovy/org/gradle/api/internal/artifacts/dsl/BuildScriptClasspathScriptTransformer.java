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

import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.Phases;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.groovy.scripts.Transformer;

import java.util.Iterator;
import java.util.List;

public class BuildScriptClasspathScriptTransformer extends AbstractScriptTransformer {
    private static final String BUILDSCRIPT_METHOD_NAME = "scriptclasspath";

    protected int getPhase() {
        return Phases.CONVERSION;
    }

    public void call(SourceUnit source) throws CompilationFailedException {
        Spec<Statement> spec = isScriptBlock();
        filterStatements(source, spec);

        source.getAST().getImports().clear();
        source.getAST().getImportPackages().clear();
        List classes = source.getAST().getClasses();
        if (!classes.isEmpty()) {
            classes.subList(1, classes.size()).clear();
            ClassNode scriptClass = (ClassNode) classes.get(0);
            if (scriptClass.getMethods().size() > 2) {
                scriptClass.getMethods().subList(2, scriptClass.getMethods().size()).clear();
            }
        }
        source.getAST().getMethods().clear();
    }

    private void filterStatements(SourceUnit source, Spec<Statement> spec) {
        Iterator statementIterator = source.getAST().getStatementBlock().getStatements().iterator();
        while (statementIterator.hasNext()) {
            Statement statement = (Statement) statementIterator.next();
            if (!spec.isSatisfiedBy(statement)) {
                statementIterator.remove();
            }
        }
    }

    public Transformer invert() {
        return new AbstractScriptTransformer() {
            protected int getPhase() {
                return Phases.CANONICALIZATION;
            }

            @Override
            public void call(SourceUnit source) throws CompilationFailedException {
                Spec<Statement> spec = Specs.not(isScriptBlock());
                filterStatements(source, spec);
            }
        };
    }

    public Spec<Statement> isScriptBlock() {
        return new Spec<Statement>() {
            public boolean isSatisfiedBy(Statement statement) {
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
        };
    }
}


