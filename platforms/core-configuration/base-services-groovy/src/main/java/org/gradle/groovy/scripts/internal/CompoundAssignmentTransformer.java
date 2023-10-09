/*
 * Copyright 2023 the original author or authors.
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

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.CompoundAssignmentSupport;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
@NonNullApi
public class CompoundAssignmentTransformer extends AbstractScriptTransformer implements ASTTransformation {
    private static final Map<String, Token> SUPPORTED_OPERATIONS = new HashMap<>();

    static {
        SUPPORTED_OPERATIONS.put("+=", Token.newSymbol(Types.PLUS, -1, -1));
        SUPPORTED_OPERATIONS.put("-=", Token.newSymbol(Types.MINUS, -1, -1));
    }

    private static final Token ASSIGN = Token.newSymbol(Types.ASSIGN, -1, -1);

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        call(source);
    }

    @Override
    public void call(SourceUnit source) {
        ClassCodeVisitorSupport visitor = new ClassCodeExpressionTransformer() {
            @Override
            public Expression transform(Expression expr) {
                if (expr instanceof BinaryExpression) {
                    return transformBinaryExpression((BinaryExpression) super.transform(expr));
                } else if (expr instanceof ClosureExpression) {
                    return transformClosureExpression((ClosureExpression) super.transform(expr));
                }
                return super.transform(expr);
            }

            private ClosureExpression transformClosureExpression(ClosureExpression expr) {
                // ClosureExpression has code inside of it, but ClosureExpression.transformExpression doesn't descent into it.
                Parameter[] parameters = expr.getParameters();
                if (parameters != null) {
                    for (Parameter parameter : parameters) {
                        if (parameter.hasInitialExpression()) {
                            parameter.setInitialExpression(transform(parameter.getInitialExpression()));
                        }
                    }
                }
                expr.getCode().visit(this);
                return expr;
            }

            private BinaryExpression transformBinaryExpression(BinaryExpression expr) {
                Expression lhs = expr.getLeftExpression();
                Expression rhs = expr.getRightExpression();
                if (isSupportedCompoundAssignment(expr) && isValidDestination(lhs)) {
                    return withSourceLocationOf(expr, new BinaryExpression(
                        lhs,
                        ASSIGN,
                        withSourceLocationOf(expr, new BinaryExpression(
                            wrapInFreeze(lhs),
                            getAugmentingOperation(expr),
                            rhs))));
                }
                return expr;
            }

            @Override
            protected SourceUnit getSourceUnit() {
                return source;
            }
        };

        ModuleNode moduleAst = source.getAST();

        moduleAst.getStatementBlock().visit(visitor);
        moduleAst.getClasses().forEach(visitor::visitClass);
        moduleAst.getMethods().forEach(visitor::visitMethod);
    }

    private static Expression wrapInFreeze(Expression expression) {
        return new StaticMethodCallExpression(ClassHelper.make(CompoundAssignmentSupport.class), "freeze", expression);
    }

    private static boolean isValidDestination(Expression expr) {
        return expr instanceof VariableExpression || expr instanceof PropertyExpression;
    }

    private static boolean isSupportedCompoundAssignment(BinaryExpression expression) {
        return SUPPORTED_OPERATIONS.containsKey(expression.getOperation().getText());
    }

    private static Token getAugmentingOperation(BinaryExpression expression) {
        return Objects.requireNonNull(SUPPORTED_OPERATIONS.get(expression.getOperation().getText()));
    }

    private static <T extends Expression> T withSourceLocationOf(Expression original, T transformed) {
        transformed.setSourcePosition(original);
        return transformed;
    }

    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }
}
