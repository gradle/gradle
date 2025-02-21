/*
 * Copyright 2025 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.gradle.api.internal.provider.support.CompoundAssignmentSupport;

import java.util.Map;

/**
 * Rewrites some compound assignment expressions, like {@code +=}, in a way they can be intercepted at runtime and a custom behavior applied.
 *
 * @see CompoundAssignmentSupport
 */
public class CompoundAssignmentTransformer extends AbstractScriptTransformer {
    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        CompoundAssignmentExpressionRewriter visitor = new CompoundAssignmentExpressionRewriter(source);
        source.getAST().getStatementBlock().visit(visitor);
        source.getAST().getClasses().forEach(visitor::visitClass);
        source.getAST().getMethods().forEach(visitor::visitMethod);
    }

    /**
     * Transforms a single AST node. This is useful for unit tests that want to apply the transform.
     *
     * @param node the ClassNode or the MethodNode
     * @param source the source unit in which the AST node resides
     * @throws CompilationFailedException if the transformation cannot be applied.
     */
    public void call(ASTNode node, SourceUnit source) throws CompilationFailedException {
        CompoundAssignmentExpressionRewriter visitor = new CompoundAssignmentExpressionRewriter(source);
        if (node instanceof ClassNode) {
            visitor.visitClass((ClassNode) node);
        } else if (node instanceof MethodNode) {
            visitor.visitMethod((MethodNode) node);
        } else {
            throw new IllegalArgumentException("Cannot apply the transformation to node " + node + " of type " + node.getClass());
        }
    }

    private static class CompoundAssignmentExpressionRewriter extends ClassCodeExpressionTransformer {
        // This only includes operators we want to transform.
        // TODO(mlopatkin): ConfigurableFileCollections support `-`, so they should probably support `-=` too.
        private static final Map<String, Integer> COMPOUND_TO_OPERATOR = ImmutableMap.of(
            "+=", Types.PLUS
        );
        private final SourceUnit sourceUnit;

        public CompoundAssignmentExpressionRewriter(SourceUnit sourceUnit) {
            this.sourceUnit = sourceUnit;
        }

        @Override
        public Expression transform(Expression expr) {
            Expression transformedExpr = super.transform(expr);
            if (transformedExpr instanceof BinaryExpression) {
                return transformBinaryExpression((BinaryExpression) transformedExpr);
            }
            if (transformedExpr instanceof ClosureExpression) {
                // Closure expression contains code, but ClosureExpression.transform doesn't descend into it.
                ClosureExpression closureExpression = (ClosureExpression) transformedExpr;
                closureExpression.visit(this);
            }
            return transformedExpr;
        }

        private Expression transformBinaryExpression(BinaryExpression expr) {
            String operation = expr.getOperation().getText();
            Integer compoundOpType = COMPOUND_TO_OPERATOR.get(operation);
            if (compoundOpType != null) {
                return transformCompoundAssignment(expr, compoundOpType);
            }
            return expr;
        }

        /**
         * Rewrites {@code foo <OP>= bar} into {@code CompoundAssignmentSupport.unwrapCompoundAssignment(foo = CompoundAssignmentSupport.forCompoundAssignment(foo <OP> bar))}.
         *
         * @param original the original compound assignment expression
         * @param compoundOperation the added operation of assignment ({@code OP})
         * @return the transformed expression
         */
        private Expression transformCompoundAssignment(BinaryExpression original, int compoundOperation) {
            Expression lhs = original.getLeftExpression();
            Expression rhs = original.getRightExpression();

            if (!isValidDestination(lhs)) {
                // Skip array element assignments and the likes?
                return original;
            }

            // This transformation is not the easiest one to deal with. It aims at several goals:
            // 1. The transformed code should preserve runtime behavior - for example, do not call `getFoo` twice when it is a property.
            // 2. The transformed code should be expressible in plain Groovy - it should not use constructs not allowed by language, like TemporaryVariableExpression.
            //    These are black magic and cause unwanted side effects when used with other AST transformations like Spock or even @CompileStatic.
            //    Our attempts to use them either caused VerificationErrors or required tight coupling with Groovy compiler internals.
            // 3. The transformed code should support static compilation.

            // foo <OP>= bar -> foo = CompoundAssignmentSupport.forCompoundAssignment(foo <OP> bar)
            BinaryExpression assignment = withSourceLocationOf(original, new BinaryExpression(
                lhs,
                rewriteToken(original.getOperation(), Types.ASSIGN),
                markForAssignment(
                    withSourceLocationOf(original, new BinaryExpression(
                        lhs,
                        rewriteToken(original.getOperation(), compoundOperation),
                        rhs
                    ))
                )
            ));

            // foo = ... -> CompoundAssignmentSupport.finishCompoundAssignment(foo = ...)
            return finishAssignment(assignment);
        }

        /**
         * Builds a new token from the original, keeping the source position.
         *
         * @param originalOperation the original operation token
         * @param newType the new token type
         * @return the new token
         */
        private Token rewriteToken(Token originalOperation, int newType) {
            return Token.newSymbol(newType, originalOperation.getStartLine(), originalOperation.getStartColumn());
        }

        private Expression markForAssignment(Expression expr) {
            return callSupportMethodOn("forCompoundAssignment", expr);
        }

        private Expression finishAssignment(Expression expr) {
            return callSupportMethodOn("finishCompoundAssignment", expr);
        }

        private Expression callSupportMethodOn(String methodName, Expression argument) {
            return withSourceLocationOf(argument, new StaticMethodCallExpression(ClassHelper.make(CompoundAssignmentSupport.class), methodName, new ArgumentListExpression(argument)));
        }

        private static boolean isValidDestination(Expression expr) {
            // We only rewrite compound assignments to non-primitive variables and properties.
            if (expr instanceof VariableExpression) {
                return !isPrimitiveVariable((VariableExpression) expr);
            } else {
                return expr instanceof PropertyExpression;
            }
        }

        private static boolean isPrimitiveVariable(VariableExpression expr) {
            return ClassHelper.isPrimitiveType(expr.getOriginType());
        }

        private static <T extends Expression> T withSourceLocationOf(Expression original, T transformed) {
            transformed.setSourcePosition(original);
            return transformed;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return sourceUnit;
        }
    }
}
