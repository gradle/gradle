/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.dsl.internal.spike;

import com.google.common.collect.Lists;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.gradle.util.CollectionUtils;

import java.util.List;

class ModelRegistryDslHelperStatementGenerator {

    private final List<Statement> generatedStatements = Lists.newArrayList();

    private String getPath(List<String> scope, VariableExpression propertyPathExpression) {
        String propertyPath = propertyPathExpression.getName();
        String scopePath = CollectionUtils.join(".", scope);
        return scopePath.length() > 0 ? String.format("%s.%s", scopePath, propertyPath): propertyPath;
    }

    public void addCreator(List<String> scope, VariableExpression propertyPathExpression, ClosureExpression creator) {
        String path = getPath(scope, propertyPathExpression);

        VariableExpression subject = new VariableExpression("modelRegistryHelper");
        ArgumentListExpression arguments = new ArgumentListExpression(new ConstantExpression(path), creator);
        MethodCallExpression methodCallExpression = new MethodCallExpression(subject, "addCreator", arguments);
        generatedStatements.add(new ExpressionStatement(methodCallExpression));
    }

    public void addCreator(List<String> scope, VariableExpression propertyPathExpression, Expression expression) {
        ClosureExpression wrappingClosure = new ClosureExpression(null, new ExpressionStatement(expression));
        wrappingClosure.setVariableScope(new VariableScope());
        addCreator(scope, propertyPathExpression, wrappingClosure);
    }

    public List<Statement> getGeneratedStatements() {
        return generatedStatements;
    }
}
