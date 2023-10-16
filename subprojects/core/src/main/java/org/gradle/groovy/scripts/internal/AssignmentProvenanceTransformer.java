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

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.internal.provider.DefaultProperty;

public class AssignmentProvenanceTransformer extends AbstractScriptTransformer {
    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        source.getAST().getStatementBlock().visit(new Visitor());
    }

    class Visitor extends ExpressionReplacingVisitorSupport {

        /**
         * a = b
         * a = DefaultProperty.withProv("...", b)
         * a = b = c
         * a = DefaultProperty.withProv("...", b = DefaultProperty.withProv("...", c))
         */
        @Override
        public void visitBinaryExpression(BinaryExpression expr) {
            if (expr.getOperation().getText().equals("=")) {
                Expression rhs = expr.getRightExpression();
                String provenance = "build.gradle:" + rhs.getLineNumber() + ":" + rhs.getColumnNumber();
                expr.setRightExpression(
                    new StaticMethodCallExpression(
                        ClassHelper.make(DefaultProperty.class),
                        "withProv",
                        new ArgumentListExpression(new ConstantExpression(provenance), rhs)
                    )
                );
                return;
            }
            super.visitBinaryExpression(expr);
        }
    }
}
