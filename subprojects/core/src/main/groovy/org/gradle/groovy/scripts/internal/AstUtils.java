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

package org.gradle.groovy.scripts.internal;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;

import java.util.ListIterator;

/**
 * Self contained utility functions for dealing with AST.
 */
public abstract class AstUtils {

    private AstUtils() {
    }

    public static boolean isMethodOnThis(MethodCallExpression call, String name) {
        boolean hasName = call.getMethod() instanceof ConstantExpression && call.getMethod().getText().equals(name);
        return hasName && targetIsThis(call);
    }

    public static boolean targetIsThis(MethodCallExpression call) {
        Expression target = call.getObjectExpression();
        return target instanceof VariableExpression && target.getText().equals("this");
    }

    public static void visitScriptCode(SourceUnit source, GroovyCodeVisitor transformer) {
        source.getAST().getStatementBlock().visit(transformer);
        for (Object method : source.getAST().getMethods()) {
            MethodNode methodNode = (MethodNode) method;
            methodNode.getCode().visit(transformer);
        }
    }

    public static ClassNode getScriptClass(SourceUnit source) {
        if (source.getAST().getStatementBlock().getStatements().isEmpty() && source.getAST().getMethods().isEmpty()) {
            // There is no script class when there are no statements or methods declared in the script
            return null;
        }
        return source.getAST().getClasses().get(0);
    }

    public static void removeMethod(ClassNode declaringClass, MethodNode methodNode) {
        declaringClass.getMethods().remove(methodNode);
        declaringClass.getDeclaredMethods(methodNode.getName()).clear();
    }

    public static void filterAndTransformStatements(SourceUnit source, StatementTransformer transformer) {
        ListIterator<Statement> statementIterator = source.getAST().getStatementBlock().getStatements().listIterator();
        while (statementIterator.hasNext()) {
            Statement originalStatement = statementIterator.next();
            Statement transformedStatement = transformer.transform(source, originalStatement);
            if (transformedStatement == null) {
                statementIterator.remove();
            } else if (transformedStatement != originalStatement) {
                statementIterator.set(transformedStatement);
            }
        }
    }

    public static boolean isVisible(SourceUnit source, String className) {
        try {
            source.getClassLoader().loadClass(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
