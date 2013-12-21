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

import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;

import java.util.Map;
import java.util.Set;

public class FilteredScriptBlockTransformer implements FilteredTransformer<Statement, Statement> {

    private final Spec<Statement> spec;
    private final Transformer<Statement, Statement> transform;

    public <T extends Transformer<? extends Statement, ? super Statement>> FilteredScriptBlockTransformer(Map<String, T> transforms) {
        this.spec = new IsScriptBlockSpec(transforms.keySet());
        this.transform = new ScriptBlockTransformer(transforms);
    }

    private static class IsScriptBlockSpec implements Spec<Statement> {
        private final Set<String> names;

        private IsScriptBlockSpec(Set<String> names) {
            this.names = names;
        }

        public boolean isSatisfiedBy(Statement statement) {
            if (!(statement instanceof ExpressionStatement)) {
                return false;
            }

            ExpressionStatement expressionStatement = (ExpressionStatement) statement;
            if (!(expressionStatement.getExpression() instanceof MethodCallExpression)) {
                return false;
            }

            MethodCallExpression methodCall = (MethodCallExpression) expressionStatement.getExpression();
            if (!isValidName(methodCall)) {
                return false;
            }

            if (!(methodCall.getArguments() instanceof ArgumentListExpression)) {
                return false;
            }

            ArgumentListExpression args = (ArgumentListExpression) methodCall.getArguments();
            return args.getExpressions().size() == 1 && args.getExpression(0) instanceof ClosureExpression;
        }

        private boolean isValidName(MethodCallExpression methodCall) {
            for (String name : names) {
                if (AstUtils.isMethodOnThis(methodCall, name)) {
                    return true;
                }
            }

            return false;
        }
    }

    public Spec<Statement> getSpec() {
        return spec;
    }

    public Transformer<Statement, Statement> getTransformer() {
        return transform;
    }

    private static class ScriptBlockTransformer implements Transformer<Statement, Statement> {
        private final Map<String, ? extends Transformer<? extends Statement, ? super Statement>> transforms;

        public <T extends Transformer<? extends Statement, ? super Statement>> ScriptBlockTransformer(Map<String, T> transforms) {
            this.transforms = transforms;
        }

        public Statement transform(Statement original) {
            ExpressionStatement expressionStatement = (ExpressionStatement) original;
            MethodCallExpression methodCall = (MethodCallExpression) expressionStatement.getExpression();
            String methodName = methodCall.getMethod().getText();

            Transformer<? extends Statement, ? super Statement> transformer = transforms.get(methodName);
            if (transformer == null) {
                throw new IllegalStateException("No transformer registered for method call '" + methodName + "'");
            }

            return transformer.transform(original);
        }
    }
}
