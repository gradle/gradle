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

package org.gradle.api.internal.groovy.support;

import com.google.common.collect.ImmutableMap;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.gradle.groovy.scripts.internal.AbstractScriptTransformer;

import java.util.Map;

/**
 * Rewrites some compound assignment expressions, like {@code +=} to help runtime distinguish them from operator calls {@code +}.
 * <p>
 * See the package documentation for the complete description.
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
    public static void call(ASTNode node, SourceUnit source) throws CompilationFailedException {
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
         * Rewrites {@code foo <OP>= bar} into {@code (foo = foo.forCompoundAssignment() <OP> (bar)).toAssignmentResult()}.
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

            // Rewriting `foo <OP>= bar` into `foo = foo.forCompoundAssignment() <OP> (bar)`.
            BinaryExpression assignment = withSourceLocationOf(original, new BinaryExpression(
                lhs,
                rewriteToken(original.getOperation(), Types.ASSIGN),
                withSourceLocationOf(original, new BinaryExpression(
                    applyForCompoundAssignment(lhs),
                    rewriteToken(original.getOperation(), compoundOperation),
                    rhs
                ))
            ));
            // Final rewrite: unwrap the result
            return withSourceLocationOf(original, applyToAssignmentResult(assignment));
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

        /**
         * @see CompoundAssignmentExtensions#forCompoundAssignment(Object)
         */
        private Expression applyForCompoundAssignment(Expression receiver) {
            return callSupportMethodOn(receiver, "forCompoundAssignment");
        }

        /**
         * @see CompoundAssignmentExtensions#toAssignmentResult(Object)
         */
        private Expression applyToAssignmentResult(Expression receiver) {
            return callSupportMethodOn(receiver, "toAssignmentResult");
        }

        private Expression callSupportMethodOn(Expression receiver, String methodName) {
            return withSourceLocationOf(receiver, new MethodCallExpression(
                receiver,
                methodName,
                MethodCallExpression.NO_ARGUMENTS)
            );
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
