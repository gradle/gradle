/*
 * Copyright 2024 the original author or authors.
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

import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassHelper;
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
import org.gradle.api.internal.provider.support.SupportsCompoundAssignment;

public class PlusAssignTransformer extends AbstractScriptTransformer {
    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        PlusAssignExpressionRewriter visitor = new PlusAssignExpressionRewriter(source);
        source.getAST().getStatementBlock().visit(visitor);
        source.getAST().getClasses().forEach(visitor::visitClass);
        source.getAST().getMethods().forEach(visitor::visitMethod);
    }

    private static class PlusAssignExpressionRewriter extends ClassCodeExpressionTransformer {
        private final SourceUnit sourceUnit;

        public PlusAssignExpressionRewriter(SourceUnit sourceUnit) {
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

        private BinaryExpression transformBinaryExpression(BinaryExpression expr) {
            String operation = expr.getOperation().getText();
            if (operation.equals("+=")) {
                return transformCompoundAssignment(expr, Types.PLUS);
            }
            return expr;
        }

        private BinaryExpression transformCompoundAssignment(BinaryExpression expr, int compoundOperation) {
            Expression lhs = expr.getLeftExpression();
            Expression rhs = expr.getRightExpression();

            if (!isValidDestination(lhs)) {
                return expr;
            }

            // Rewriting `foo <OP>= bar` into `foo = SupportsCompoundAssignment.wrap(foo) <OP> (bar)`.
            BinaryExpression transformed = new BinaryExpression(
                lhs,
                rewriteToken(expr.getOperation(), Types.ASSIGN),
                new BinaryExpression(
                    wrapLhs(lhs),
                    rewriteToken(expr.getOperation(), compoundOperation),
                    rhs
                )
            );
            return withSourceLocationOf(expr, transformed);
        }

        private Token rewriteToken(Token originalOperation, int newType) {
            return Token.newSymbol(newType, originalOperation.getStartLine(), originalOperation.getStartColumn());
        }

        private Expression wrapLhs(Expression lhs) {
            return new StaticMethodCallExpression(ClassHelper.make(SupportsCompoundAssignment.class), "wrap", new ArgumentListExpression(lhs));
        }

        private static boolean isValidDestination(Expression expr) {
            return expr instanceof VariableExpression || expr instanceof PropertyExpression;
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
