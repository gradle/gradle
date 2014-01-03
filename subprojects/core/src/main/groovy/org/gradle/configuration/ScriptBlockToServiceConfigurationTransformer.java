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

package org.gradle.configuration;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.gradle.api.Transformer;
import org.gradle.util.ConfigureUtil;

public class ScriptBlockToServiceConfigurationTransformer implements Transformer<Statement, Statement> {

    private final String servicesFieldName;
    private final Class<?> serviceClass;

    public ScriptBlockToServiceConfigurationTransformer(String servicesFieldName, Class<?> serviceClass) {
        this.servicesFieldName = servicesFieldName;
        this.serviceClass = serviceClass;
    }

    public Statement transform(Statement original) {
        ExpressionStatement expressionStatement = (ExpressionStatement) original;
        MethodCallExpression methodCall = (MethodCallExpression) expressionStatement.getExpression();
        ArgumentListExpression args = (ArgumentListExpression) methodCall.getArguments();
        Expression closureArg = args.getExpression(0);

        PropertyExpression servicesProperty = new PropertyExpression(VariableExpression.THIS_EXPRESSION, servicesFieldName);
        MethodCallExpression getServiceMethodCall = new MethodCallExpression(servicesProperty, "get",
                new ArgumentListExpression(
                        new ClassExpression(new ClassNode(serviceClass))
                )
        );

        ClassNode configureUtilClass = new ClassNode(ConfigureUtil.class);

        // Remove access to any surrounding context
        Expression hydrateMethodCall = new MethodCallExpression(closureArg, "rehydrate", new ArgumentListExpression(
                ConstantExpression.NULL, getServiceMethodCall, getServiceMethodCall
        ));

        Expression closureCall = new MethodCallExpression(hydrateMethodCall, "call", ArgumentListExpression.EMPTY_ARGUMENTS);

        return new ExpressionStatement(closureCall);
    }
}
