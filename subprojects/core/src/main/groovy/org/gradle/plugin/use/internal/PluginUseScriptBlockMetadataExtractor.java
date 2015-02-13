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

import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.internal.RestrictiveCodeVisitor;
import org.gradle.groovy.scripts.internal.ScriptBlock;
import org.gradle.plugin.internal.InvalidPluginIdException;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.PluginDependencySpec;

import static org.gradle.groovy.scripts.internal.AstUtils.isString;

public class PluginUseScriptBlockMetadataExtractor {

    public static final String INVALID_ARGUMENT_LIST = "argument list must be exactly 1 literal non empty string";
    public static final String BASE_MESSAGE = "only id(String) method calls allowed in plugins {} script block";
    public static final String VERSION_MESSAGE = "only version(String) method calls allowed in plugins {} script block";
    private static final String NOT_LITERAL_METHOD_NAME = "method name must be literal (i.e. not a variable)";
    private static final String NOT_LITERAL_ID_METHOD_NAME = BASE_MESSAGE + " - " + NOT_LITERAL_METHOD_NAME;

    private final DocumentationRegistry documentationRegistry;
    private final PluginRequestCollector pluginRequestCollector;

    public PluginUseScriptBlockMetadataExtractor(ScriptSource scriptSource, DocumentationRegistry documentationRegistry) {
        this.pluginRequestCollector = new PluginRequestCollector(scriptSource);
        this.documentationRegistry = documentationRegistry;
    }

    public void extract(SourceUnit sourceUnit, ScriptBlock scriptBlock) {
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
                    if (isString(methodName)) {
                        String methodNameText = methodName.getText();
                        if (methodNameText.equals("id") || methodNameText.equals("version")) {
                            ConstantExpression argumentExpression = hasSingleConstantStringArg(call);
                            if (argumentExpression == null) {
                                return;
                            }

                            String argStringValue = argumentExpression.getValue().toString();
                            if (argStringValue.length() == 0) {
                                restrict(argumentExpression, formatErrorMessage(INVALID_ARGUMENT_LIST));
                                return;
                            }

                            if (methodName.getText().equals("id")) {
                                if (call.isImplicitThis()) {
                                    try {
                                        PluginId.validate(argStringValue);
                                        call.setNodeMetaData(PluginDependencySpec.class, pluginRequestCollector.createSpec(call.getLineNumber()).id(argStringValue));
                                    } catch (InvalidPluginIdException e) {
                                        restrict(argumentExpression, formatErrorMessage(e.getReason()));
                                    }
                                } else {
                                    restrict(call, formatErrorMessage(BASE_MESSAGE));
                                }
                            }

                            if (methodName.getText().equals("version")) {
                                Expression objectExpression = call.getObjectExpression();
                                if (objectExpression instanceof MethodCallExpression) {
                                    PluginDependencySpec spec = objectExpression.getNodeMetaData(PluginDependencySpec.class);
                                    if (spec != null) {
                                        spec.version(argStringValue);
                                    }
                                } else {
                                    restrict(call, formatErrorMessage(BASE_MESSAGE));
                                }
                            }
                        } else {
                            if (!call.isImplicitThis()) {
                                restrict(methodName, formatErrorMessage(VERSION_MESSAGE));
                            } else {
                                restrict(methodName, formatErrorMessage(BASE_MESSAGE));
                            }
                        }
                    } else {
                        restrict(methodName, formatErrorMessage(NOT_LITERAL_ID_METHOD_NAME));
                    }
                } else {
                    restrict(call);
                }
            }

            private ConstantExpression hasSingleConstantStringArg(MethodCallExpression call) {
                ArgumentListExpression argumentList = (ArgumentListExpression) call.getArguments();
                if (argumentList.getExpressions().size() == 1) {
                    Expression argumentExpression = argumentList.getExpressions().get(0);
                    if (argumentExpression instanceof ConstantExpression) {
                        ConstantExpression constantArgumentExpression = (ConstantExpression) argumentExpression;
                        if (isString(constantArgumentExpression)) {
                            return constantArgumentExpression;
                        } else {
                            restrict(constantArgumentExpression, formatErrorMessage(INVALID_ARGUMENT_LIST));
                        }
                    } else {
                        restrict(argumentExpression, formatErrorMessage(INVALID_ARGUMENT_LIST));
                    }
                } else {
                    restrict(argumentList, formatErrorMessage(INVALID_ARGUMENT_LIST));
                }

                return null;
            }

            @Override
            public void visitExpressionStatement(ExpressionStatement statement) {
                statement.getExpression().visit(this);
            }
        });
    }

    public PluginRequests getRequests() {
        return new DefaultPluginRequests(pluginRequestCollector.getRequests());
    }

    public String formatErrorMessage(String message) {
        return String.format("%s%n%nSee %s for information on the plugins {} block%n%n", message, documentationRegistry.getDocumentationFor("plugins", "sec:plugins_block"));
    }
}
