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

package org.gradle.plugin.use.internal;

import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.groovy.scripts.internal.RestrictiveCodeVisitor;
import org.gradle.groovy.scripts.internal.ScriptBlock;

import static org.gradle.groovy.scripts.internal.AstUtils.hasSingleConstantArgOfType;
import static org.gradle.groovy.scripts.internal.AstUtils.isOfType;

public class PluginUseScriptBlockMetadataCompiler {

    public static final String NEED_SINGLE_BOOLEAN = "argument list must be exactly 1 literal boolean";
    public static final String NEED_LITERAL_STRING = "argument list must be exactly 1 literal String or String with property replacement";
    public static final String NEED_INTERPOLATED_STRING = "argument list must be exactly 1 literal String or String with property replacement";
    public static final String BASE_MESSAGE = "only id(String) method calls allowed in plugins {} script block";
    public static final String EXTENDED_MESSAGE = "only version(String) and apply(boolean) method calls allowed in plugins {} script block";
    private static final String NOT_LITERAL_METHOD_NAME = "method name must be literal (i.e. not a variable)";
    private static final String NOT_LITERAL_ID_METHOD_NAME = BASE_MESSAGE + " - " + NOT_LITERAL_METHOD_NAME;

    private final DocumentationRegistry documentationRegistry;

    public PluginUseScriptBlockMetadataCompiler(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
    }

    public void compile(SourceUnit sourceUnit, ScriptBlock scriptBlock) {
        ClosureExpression closureArg = scriptBlock.getClosureExpression();

        closureArg.getCode().visit(new RestrictiveCodeVisitor(sourceUnit, formatErrorMessage(BASE_MESSAGE)) {

            @Override
            public void visitBlockStatement(BlockStatement block) {
                for (Statement statement : block.getStatements()) {
                    statement.visit(this);
                }
            }

            @Override
            public void visitMethodCallExpression(MethodCallExpression call) {
                if (!call.isImplicitThis()) {
                    Expression target = call.getObjectExpression();
                    if (!(target instanceof MethodCallExpression)) {
                        restrict(target, formatErrorMessage(BASE_MESSAGE));
                        return;
                    }

                    visitMethodCallExpression((MethodCallExpression) target);
                }

                if (call.getMethod() instanceof ConstantExpression) {
                    ConstantExpression methodName = (ConstantExpression) call.getMethod();
                    if (isOfType(methodName, String.class)) {
                        String methodNameText = methodName.getText();
                        switch (methodNameText) {
                            case "id":
                                ConstantExpression argumentExpression = hasSingleConstantArgOfType(call, String.class);
                                if (argumentExpression == null) {
                                    restrict(call, formatErrorMessage(NEED_LITERAL_STRING));
                                    return;
                                }

                                if (!call.isImplicitThis()) {
                                    restrict(call, formatErrorMessage(BASE_MESSAGE));
                                } else {
                                    ConstantExpression lineNumberExpression = new ConstantExpression(call.getLineNumber(), true);
                                    call.setArguments(new ArgumentListExpression(argumentExpression, lineNumberExpression));
                                }

                                break;
                            case "version":
                                if (!hasSimpleInterpolatedStringType(call)) {
                                    restrict(call, formatErrorMessage(NEED_INTERPOLATED_STRING));
                                    return;
                                }

                                if (call.isImplicitThis()) {
                                    restrict(call, formatErrorMessage(BASE_MESSAGE));
                                }
                                break;
                            case "apply":
                                ConstantExpression arguments = hasSingleConstantArgOfType(call, boolean.class);
                                if (arguments == null) {
                                    restrict(call, formatErrorMessage(NEED_SINGLE_BOOLEAN));
                                } else if (call.isImplicitThis()) {
                                    restrict(call, formatErrorMessage(BASE_MESSAGE));
                                }
                                break;
                            default:
                                if (!call.isImplicitThis()) {
                                    restrict(methodName, formatErrorMessage(EXTENDED_MESSAGE));
                                } else {
                                    restrict(methodName, formatErrorMessage(BASE_MESSAGE));
                                }
                                break;
                        }
                    } else {
                        restrict(methodName, formatErrorMessage(NOT_LITERAL_ID_METHOD_NAME));
                    }
                } else {
                    restrict(call);
                }
            }

            @Override
            public void visitExpressionStatement(ExpressionStatement statement) {
                statement.getExpression().visit(this);
            }
        });
    }

    /**
     * Checks if this method has a single argument that is either:
     * a) A constant String expression
     * b) A GString expression containing only variable expressions
     */
    private static boolean hasSimpleInterpolatedStringType(MethodCallExpression call) {
        if (hasSingleConstantArgOfType(call, String.class) != null) {
            return true;
        }

        ArgumentListExpression argumentList = (ArgumentListExpression) call.getArguments();
        if (argumentList.getExpressions().size() == 1) {
            Expression argumentExpression = argumentList.getExpressions().get(0);
            if (argumentExpression instanceof GStringExpression) {
                GStringExpression gStringExpression = (GStringExpression) argumentExpression;
                for (Expression value : gStringExpression.getValues()) {
                    if (!(value instanceof VariableExpression)) {
                        return false;
                    }
                }

                return true;
            }
        }
        return false;
    }

    public String formatErrorMessage(String message) {
        return String.format("%s%n%nSee %s for information on the plugins {} block%n%n", message, documentationRegistry.getDocumentationFor("plugins", "sec:plugins_block"));
    }
}
