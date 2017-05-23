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

import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.internal.RestrictiveCodeVisitor;
import org.gradle.groovy.scripts.internal.ScriptBlock;
import org.gradle.plugin.internal.InvalidPluginIdException;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.BinaryPluginDependencySpec;
import org.gradle.plugin.use.PluginDependencySpec;
import org.gradle.plugin.use.ScriptPluginDependencySpec;

import static org.gradle.groovy.scripts.internal.AstUtils.hasSingleConstantArgOfType;
import static org.gradle.groovy.scripts.internal.AstUtils.isOfType;

public class PluginUseScriptBlockMetadataExtractor {

    public static final String NEED_SINGLE_BOOLEAN = "argument list must be exactly 1 literal boolean";
    public static final String NEED_SINGLE_STRING = "argument list must be exactly 1 literal non empty string";
    public static final String BASE_MESSAGE = "only id(String) and script(String) method calls allowed in plugins {} script block";
    public static final String EXTENDED_MESSAGE = "only version(String) and apply(boolean) method calls allowed in plugins {} script block";
    private static final String SCRIPT_PLUGINS_APPLY_FALSE_UNSUPPORTED = "apply false is not supported for script plugins applied using the plugins {} script block";
    private static final String SCRIPT_PLUGINS_VERSION_UNSUPPORTED = "version is not supported for script plugins applied using the plugins {} script block";
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
                    if (isOfType(methodName, String.class)) {
                        String methodNameText = methodName.getText();
                        if (methodNameText.equals("id") || methodNameText.equals("script") || methodNameText.equals("version")) {
                            ConstantExpression argumentExpression = hasSingleConstantArgOfType(call, String.class);
                            if (argumentExpression == null) {
                                restrict(call, formatErrorMessage(NEED_SINGLE_STRING));
                                return;
                            }

                            String argStringValue = argumentExpression.getValue().toString();
                            if (argStringValue.length() == 0) {
                                restrict(argumentExpression, formatErrorMessage(NEED_SINGLE_STRING));
                                return;
                            }

                            if (methodName.getText().equals("id")) {
                                if (call.isImplicitThis()) {
                                    try {
                                        DefaultPluginId.validate(argStringValue);
                                        call.setNodeMetaData(BinaryPluginDependencySpec.class, pluginRequestCollector.createSpec(call.getLineNumber()).id(argStringValue));
                                    } catch (InvalidPluginIdException e) {
                                        restrict(argumentExpression, formatErrorMessage(e.getReason()));
                                    }
                                } else {
                                    restrict(call, formatErrorMessage(BASE_MESSAGE));
                                }
                            }

                            if (methodNameText.equals("script")) {
                                if (call.isImplicitThis()) {
                                    call.setNodeMetaData(ScriptPluginDependencySpec.class, pluginRequestCollector.createSpec(call.getLineNumber()).script(argStringValue));
                                } else {
                                    restrict(call, formatErrorMessage(BASE_MESSAGE));
                                }
                            }

                            if (methodName.getText().equals("version")) {
                                if (getSpecFor(call, ScriptPluginDependencySpec.class) != null) {
                                    restrict(methodName, formatErrorMessage(SCRIPT_PLUGINS_VERSION_UNSUPPORTED));
                                    return;
                                }
                                BinaryPluginDependencySpec spec = getSpecFor(call, BinaryPluginDependencySpec.class);
                                if (spec == null) {
                                    return;
                                }
                                spec.version(argStringValue);
                                call.setNodeMetaData(BinaryPluginDependencySpec.class, spec);
                            }
                        } else if (methodNameText.equals("apply")) {
                            if (getSpecFor(call, ScriptPluginDependencySpec.class) != null) {
                                restrict(methodName, formatErrorMessage(SCRIPT_PLUGINS_APPLY_FALSE_UNSUPPORTED));
                                return;
                            }
                            ConstantExpression arguments = hasSingleConstantArgOfType(call, boolean.class);
                            if (arguments == null) {
                                restrict(call, formatErrorMessage(NEED_SINGLE_BOOLEAN));
                                return;
                            }
                            BinaryPluginDependencySpec spec = getSpecFor(call, BinaryPluginDependencySpec.class);
                            if (spec == null) {
                                return;
                            }
                            spec.apply((Boolean) arguments.getValue());
                        } else {
                            if (!call.isImplicitThis()) {
                                restrict(methodName, formatErrorMessage(EXTENDED_MESSAGE));
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

            private <T extends PluginDependencySpec> T getSpecFor(MethodCallExpression call, Class<T> specType) {
                Expression objectExpression = call.getObjectExpression();
                if (objectExpression instanceof MethodCallExpression) {
                    return objectExpression.getNodeMetaData(specType);
                } else {
                    restrict(call, formatErrorMessage(BASE_MESSAGE));
                    return null;
                }
            }

            @Override
            public void visitExpressionStatement(ExpressionStatement statement) {
                statement.getExpression().visit(this);
            }
        });
    }

    public PluginRequests getPluginRequests() {
        return pluginRequestCollector.getPluginRequests();
    }

    public String formatErrorMessage(String message) {
        return String.format("%s%n%nSee %s for information on the plugins {} block%n%n", message, documentationRegistry.getDocumentationFor("plugins", "sec:plugins_block"));
    }
}
