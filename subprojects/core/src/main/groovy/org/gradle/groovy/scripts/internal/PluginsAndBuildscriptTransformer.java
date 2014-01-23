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
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.ErrorCollector;
import org.gradle.api.specs.Spec;
import org.gradle.groovy.scripts.DefaultScript;
import org.gradle.plugin.PluginHandler;

public class PluginsAndBuildscriptTransformer implements StatementTransformer {

    private static final String PLUGINS = "plugins";
    private static final ScriptBlockToServiceConfigurationTransformer PLUGIN_BLOCK_TRANSFORMER = new ScriptBlockToServiceConfigurationTransformer(DefaultScript.SCRIPT_SERVICES_PROPERTY, PluginHandler.class);

    private final String classpathBlockName;

    public PluginsAndBuildscriptTransformer(String classpathBlockName) {
        this.classpathBlockName = classpathBlockName;
    }

    public Statement transform(ErrorCollector errorCollector, Statement statement) {
        ScriptBlock scriptBlock = detectScriptBlock(statement);
        if (scriptBlock == null) {
            return null;
        } else {
            if (scriptBlock.getName().equals(PLUGINS)) {
                return PLUGIN_BLOCK_TRANSFORMER.transform(scriptBlock);
            } else {
                return statement; // don't transform classpathBlockName
            }
        }
    }

    public Spec<Statement> getSpec() {
        return new Spec<Statement>() {
            public boolean isSatisfiedBy(Statement statement) {
                return detectScriptBlock(statement) != null;
            }
        };
    }

    // returns null if the statement is not a script block
    private ScriptBlock detectScriptBlock(Statement statement) {
        if (!(statement instanceof ExpressionStatement)) {
            return null;
        }

        ExpressionStatement expressionStatement = (ExpressionStatement) statement;
        if (!(expressionStatement.getExpression() instanceof MethodCallExpression)) {
            return null;
        }

        MethodCallExpression methodCall = (MethodCallExpression) expressionStatement.getExpression();
        if (!AstUtils.targetIsThis(methodCall)) {
            return null;
        }

        if (!(methodCall.getMethod() instanceof ConstantExpression)) {
            return null;
        }

        String methodName = methodCall.getMethod().getText();

        if (methodName.equals(PLUGINS) || methodName.equals(classpathBlockName)) {
            if (!(methodCall.getArguments() instanceof ArgumentListExpression)) {
                return null;
            }

            ArgumentListExpression args = (ArgumentListExpression) methodCall.getArguments();
            if (args.getExpressions().size() == 1 && args.getExpression(0) instanceof ClosureExpression) {
                return new ScriptBlock(methodName, (ClosureExpression) args.getExpression(0));
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

}
