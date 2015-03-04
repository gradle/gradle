/*
 * Copyright 2010 the original author or authors.
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

import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;

import java.util.Collections;
import java.util.List;

public class TaskDefinitionScriptTransformer extends AbstractScriptTransformer {
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    public void call(SourceUnit source) throws CompilationFailedException {
        AstUtils.visitScriptCode(source, new TaskDefinitionTransformer());
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
            if (args.getExpressions().size() == 0 || args.getExpressions().size() > 3) {
                return;
            }

            // Matches: task <arg>{1, 3}

            if (args.getExpressions().size() > 1) {
                if (args.getExpression(0) instanceof MapExpression && args.getExpression(1) instanceof VariableExpression) {
                    // Matches: task <name-value-pairs>, <identifier>, <arg>?
                    // Map to: task(<name-value-pairs>, '<identifier>', <arg>?)
                    transformVariableExpression(call, 1);
                } else if (args.getExpression(0) instanceof VariableExpression) {
                    // Matches: task <identifier>, <arg>?
                    transformVariableExpression(call, 0);
                }
                return;
            }

            // Matches: task <arg> or task(<arg>)

            Expression arg = args.getExpression(0);
            if (arg instanceof VariableExpression) {
                // Matches: task <identifier> or task(<identifier>)
                transformVariableExpression(call, 0);
            } else if (arg instanceof BinaryExpression) {
                // Matches: task <expression> <operator> <expression>
                transformBinaryExpression(call, (BinaryExpression) arg);
            } else if (arg instanceof MethodCallExpression) {
                // Matches: task <method-call>
                maybeTransformNestedMethodCall((MethodCallExpression) arg, call);
            }
        }

        private void transformVariableExpression(MethodCallExpression call, int index) {
            ArgumentListExpression args = (ArgumentListExpression) call.getArguments();
            VariableExpression arg = (VariableExpression) args.getExpression(index);
            if (!isDynamicVar(arg)) {
                return;
            }

            // Matches: task args?, <identifier>, args? or task(args?, <identifier>, args?)
            // Map to: task(args?, '<identifier>', args?)
            String taskName = arg.getText();
            call.setMethod(new ConstantExpression("task"));
            args.getExpressions().set(index, new ConstantExpression(taskName));
        }

        private void transformBinaryExpression(MethodCallExpression call, BinaryExpression expression) {

            // Matches: task <expression> <operator> <expression>

            if (expression.getLeftExpression() instanceof VariableExpression || expression.getLeftExpression() instanceof GStringExpression || expression
                    .getLeftExpression() instanceof ConstantExpression) {
                // Matches: task <identifier> <operator> <expression> | task <string> <operator> <expression>
                // Map to: passThrough(task('<identifier>') <operator> <expression>) | passThrough(task(<string>) <operator> <expression>)
                call.setMethod(new ConstantExpression("passThrough"));
                Expression argument;
                if (expression.getLeftExpression() instanceof VariableExpression) {
                    argument = new ConstantExpression(expression.getLeftExpression().getText());
                } else {
                    argument = expression.getLeftExpression();
                }
                expression.setLeftExpression(new MethodCallExpression(call.getObjectExpression(), "task", argument));
            } else if (expression.getLeftExpression() instanceof MethodCallExpression) {
                // Matches: task <method-call> <operator> <expression>
                MethodCallExpression transformedCall = new MethodCallExpression(call.getObjectExpression(), "task", new ArgumentListExpression());
                boolean transformed = maybeTransformNestedMethodCall((MethodCallExpression) expression.getLeftExpression(), transformedCall);
                if (transformed) {
                    // Matches: task <identifier> <arg-list> <operator> <expression>
                    // Map to: passThrough(task('<identifier>', <arg-list>) <operator> <expression>)
                    call.setMethod(new ConstantExpression("passThrough"));
                    expression.setLeftExpression(transformedCall);
                }
            }
        }

        private boolean maybeTransformNestedMethodCall(MethodCallExpression nestedMethod, MethodCallExpression target) {
            if (!(isTaskIdentifier(nestedMethod.getMethod()) && AstUtils.targetIsThis(nestedMethod))) {
                return false;
            }

            // Matches: task <identifier> <arg-list> | task <string> <arg-list>
            // Map to: task("<identifier>", <arg-list>) | task(<string>, <arg-list>)

            Expression taskName = nestedMethod.getMethod();
            Expression mapArg = null;
            List<Expression> extraArgs = Collections.emptyList();

            if (nestedMethod.getArguments() instanceof TupleExpression) {
                TupleExpression nestedArgs = (TupleExpression) nestedMethod.getArguments();
                if (nestedArgs.getExpressions().size() == 2 && nestedArgs.getExpression(0) instanceof MapExpression && nestedArgs.getExpression(1) instanceof ClosureExpression) {
                    // Matches: task <identifier>(<options-map>) <closure>
                    mapArg = nestedArgs.getExpression(0);
                    extraArgs = nestedArgs.getExpressions().subList(1, nestedArgs.getExpressions().size());
                } else if (nestedArgs.getExpressions().size() == 1 && nestedArgs.getExpression(0) instanceof ClosureExpression) {
                    // Matches: task <identifier> <closure>
                    extraArgs = nestedArgs.getExpressions();
                } else if (nestedArgs.getExpressions().size() == 1 && nestedArgs.getExpression(0) instanceof NamedArgumentListExpression) {
                    // Matches: task <identifier>(<options-map>)
                    mapArg = nestedArgs.getExpression(0);
                } else if (nestedArgs.getExpressions().size() != 0) {
                    return false;
                }
            }

            target.setMethod(new ConstantExpression("task"));
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
            boolean isTaskMethod = AstUtils.isMethodOnThis(call, name);
            if (!isTaskMethod) {
                return false;
            }

            return call.getArguments() instanceof ArgumentListExpression;
        }

        private boolean isTaskIdentifier(Expression expression) {
            return expression instanceof ConstantExpression || expression instanceof GStringExpression;
        }

        private boolean isDynamicVar(Expression expression) {
            if (!(expression instanceof VariableExpression)) {
                return false;
            }
            VariableExpression variableExpression = (VariableExpression) expression;
            return variableExpression.getAccessedVariable() instanceof DynamicVariable;
        }
    }
}
