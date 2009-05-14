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

import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.expr.*;

import java.util.List;
import java.util.Collections;

public class BuildScriptTransformer extends CompilationUnit.SourceUnitOperation {
    public void call(SourceUnit source) throws CompilationFailedException {
        GroovyCodeVisitor transformer = new TaskDefinitionTransformer();
        source.getAST().getStatementBlock().visit(transformer);
        for (Object method : source.getAST().getMethods()) {
            MethodNode methodNode = (MethodNode) method;
            methodNode.getCode().visit(transformer);
        }
    }

    private class TaskDefinitionTransformer extends CodeVisitorSupport {
        @Override
        public void visitMethodCallExpression(MethodCallExpression call) {
            doVisitMethodCallExpression(call);
            super.visitMethodCallExpression(call);
        }

        private void doVisitMethodCallExpression(MethodCallExpression call) {
            if (!isInstanceMethod(call, "task")) {
                return;
            }

            ArgumentListExpression args = (ArgumentListExpression) call.getArguments();
            if (args.getExpressions().size() == 0 || args.getExpressions().size() > 2) {
                return;
            }

            // Matches: task <arg> or task <arg> <arg>

            if (args.getExpressions().size() == 2) {
                if (args.getExpression(0) instanceof MapExpression && args.getExpression(1) instanceof VariableExpression) {
                    // Matches: task <name-value-pairs>, <identifier>
                    // Map to: task(<name-value-pairs>, '<identifier>')
                    args.getExpressions().set(1, new ConstantExpression(args.getExpression(1).getText()));
                }
                return;
            }

            // Matches: task <arg> or task(<arg>)

            Expression arg = args.getExpression(0);
            if (arg instanceof VariableExpression) {
                // Matches: task <identifier> or task(<identifier>)
                transformVariableExpression(call, (VariableExpression) arg);
            }
            else if (arg instanceof BinaryExpression) {
                // Matches: task <expression> <operator> <expression>
                transformBinaryExpression(call, (BinaryExpression) arg);
            }
            else if (arg instanceof MethodCallExpression) {
                // Matches: task <method-call>
                maybeTransformNestedMethodCall((MethodCallExpression) arg, call);
            }
        }

        private void transformVariableExpression(MethodCallExpression call, VariableExpression arg) {
            // Matches: task <identifier> or task(<identifier>)
            // Map to: task('<identifier>')
            String taskName = arg.getText();
            call.setMethod(new ConstantExpression("task"));
            ArgumentListExpression args = (ArgumentListExpression) call.getArguments();
            args.getExpressions().clear();
            args.addExpression(new ConstantExpression(taskName));
        }

        private void transformBinaryExpression(MethodCallExpression call, BinaryExpression expression) {

            // Matches: task <expression> <operator> <expression>

            if (expression.getLeftExpression() instanceof VariableExpression
                    || expression.getLeftExpression() instanceof GStringExpression
                    || expression.getLeftExpression() instanceof ConstantExpression) {
                // Matches: task <identifier> <operator> <expression> | task <string> <operator> <expression>
                // Map to: passThrough(createTask('<identifier>') <operator> <expression>) | passThrough(createTask(<string>) <operator> <expression>)
                call.setMethod(new ConstantExpression("passThrough"));
                Expression argument;
                if (expression.getLeftExpression() instanceof VariableExpression) {
                    argument = new ConstantExpression(expression.getLeftExpression().getText());
                } else {
                    argument = expression.getLeftExpression();
                }
                expression.setLeftExpression(new MethodCallExpression(call.getObjectExpression(), "createTask", argument));
            }
            else if (expression.getLeftExpression() instanceof MethodCallExpression) {
                // Matches: task <method-call> <operator> <expression>
                MethodCallExpression transformedCall = new MethodCallExpression(call.getObjectExpression(),
                        "createTask", new ArgumentListExpression());
                boolean transformed = maybeTransformNestedMethodCall(
                        (MethodCallExpression) expression.getLeftExpression(), transformedCall);
                if (transformed) {
                    // Matches: task <identifier> <arg-list> <operator> <expression>
                    // Map to: passThrough(createTask('<identifier>', <arg-list>) <operator> <expression>)
                    call.setMethod(new ConstantExpression("passThrough"));
                    expression.setLeftExpression(transformedCall);
                }
            }
        }

        private boolean maybeTransformNestedMethodCall(MethodCallExpression nestedMethod, MethodCallExpression target) {
            if (!(isTaskIdentifier(nestedMethod.getMethod()) && targetIsThis(nestedMethod))) {
                return false;
            }

            // Matches: task <identifier> <arg-list> | task <string> <arg-list>
            // Map to: createTask("<identifier>", <arg-list>) | createTask(<string>, <arg-list>)

            Expression taskName = nestedMethod.getMethod();
            Expression mapArg = null;
            List<Expression> extraArgs = Collections.emptyList();

            if (nestedMethod.getArguments() instanceof NamedArgumentListExpression) {
                mapArg = nestedMethod.getArguments();
            } else if (nestedMethod.getArguments() instanceof ArgumentListExpression) {
                ArgumentListExpression nestedArgs = (ArgumentListExpression) nestedMethod.getArguments();
                if (nestedArgs.getExpressions().size() > 0 && nestedArgs.getExpression(0) instanceof MapExpression) {
                    mapArg = nestedArgs.getExpression(0);
                    extraArgs = nestedArgs.getExpressions().subList(1, nestedArgs.getExpressions().size());
                } else {
                    extraArgs = nestedArgs.getExpressions();
                }
            }

            target.setMethod(new ConstantExpression("createTask"));
            ArgumentListExpression args = (ArgumentListExpression) target.getArguments();
            args.getExpressions().clear();
            if (mapArg != null) {
                args.addExpression(mapArg);
            }
            args.addExpression(taskName);
            for (Expression extraArg : extraArgs) {
                args.addExpression(extraArg);
            }
            return true;
        }

        private boolean isInstanceMethod(MethodCallExpression call, String name) {
            boolean isTaskMethod = call.getMethod() instanceof ConstantExpression && call.getMethod().getText().equals(
                    name);
            if (!isTaskMethod) {
                return false;
            }

            if (!(call.getArguments() instanceof ArgumentListExpression)) {
                return false;
            }

            return targetIsThis(call);
        }

        private boolean targetIsThis(MethodCallExpression call) {
            Expression target = call.getObjectExpression();
            return target instanceof VariableExpression && target.getText().equals("this");
        }

        private boolean isTaskIdentifier(Expression expression) {
            return expression instanceof ConstantExpression || expression instanceof GStringExpression;
        }
    }
}
