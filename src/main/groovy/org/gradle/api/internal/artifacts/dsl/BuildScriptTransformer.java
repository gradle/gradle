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
import org.codehaus.groovy.ast.expr.*;

import java.util.List;
import java.util.Collections;

public class BuildScriptTransformer extends CompilationUnit.SourceUnitOperation {
    public void call(SourceUnit source) throws CompilationFailedException {
        source.getAST().getStatementBlock().visit(new CodeVisitorSupport() {
            @Override
            public void visitMethodCallExpression(MethodCallExpression call) {
                if (!isInstanceMethod(call, "task")) {
                    super.visitMethodCallExpression(call);
                    return;
                }

                ArgumentListExpression args = (ArgumentListExpression) call.getArguments();
                if (args.getExpressions().size() != 1) {
                    super.visitMethodCallExpression(call);
                    return;
                }

                if (args.getExpression(0) instanceof VariableExpression) {
                    String taskName = args.getExpression(0).getText();
                    call.setMethod(new ConstantExpression("createTask"));
                    args.getExpressions().clear();
                    args.addExpression(new ConstantExpression(taskName));
                    return;
                }

                if (!(args.getExpression(0) instanceof MethodCallExpression)) {
                    super.visitMethodCallExpression(call);
                    return;
                }

                MethodCallExpression nestedMethod = (MethodCallExpression) args.getExpressions().get(0);
                if (!isInstanceMethod(nestedMethod)) {
                    super.visitMethodCallExpression(call);
                    return;
                }

                Expression taskName = nestedMethod.getMethod();
                Expression mapArg = null;
                List<Expression> extraArgs = Collections.emptyList();

                if (nestedMethod.getArguments() instanceof NamedArgumentListExpression) {
                    mapArg = nestedMethod.getArguments();
                }
                else if (nestedMethod.getArguments() instanceof ArgumentListExpression) {
                    ArgumentListExpression nestedArgs = (ArgumentListExpression) nestedMethod.getArguments();
                    if (nestedArgs.getExpression(0) instanceof MapExpression) {
                        mapArg = nestedArgs.getExpression(0);
                        extraArgs = nestedArgs.getExpressions().subList(1, nestedArgs.getExpressions().size());
                    }
                    else {
                        extraArgs = nestedArgs.getExpressions();
                    }
                }

                call.setMethod(new ConstantExpression("createTask"));
                args.getExpressions().clear();
                if (mapArg != null) {
                    args.addExpression(mapArg);
                }
                args.addExpression(taskName);
                for (Expression extraArg : extraArgs) {
                    args.addExpression(extraArg);
                }
            }
        });
    }

    private boolean isInstanceMethod(MethodCallExpression call, String name) {
        boolean isTaskMethod = call.getMethod() instanceof ConstantExpression && call.getMethod().getText()
                .equals(name);
        if (!isTaskMethod) {
            return false;
        }

        if (!(call.getArguments() instanceof ArgumentListExpression)) {
            return false;
        }

        return call.getObjectExpression() instanceof VariableExpression && call.getObjectExpression().getText().equals("this");
    }

    private boolean isInstanceMethod(MethodCallExpression call) {
        boolean isTaskMethod = call.getMethod() instanceof ConstantExpression || call
                .getMethod() instanceof GStringExpression;
        if (!isTaskMethod) {
            return false;
        }

        return call.getObjectExpression() instanceof VariableExpression && call.getObjectExpression().getText().equals(
                "this");
    }
}
