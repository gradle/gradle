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
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.gradle.util.CollectionUtils;

import java.util.List;

class ModelRegistryDslHelperStatementGenerator {

    private final List<Statement> generatedStatements = Lists.newArrayList();

    public void addCreator(List<String> scope, VariableExpression propertyPathExpression, ClosureExpression creator) {
        String propertyPath = propertyPathExpression.getName();
        String scopePath = CollectionUtils.join(".", scope);
        String path = scopePath.length() > 0 ? String.format("%s.%s", scopePath, propertyPath): propertyPath;

        VariableExpression subject = new VariableExpression("modelRegistryHelper");
        ArgumentListExpression arguments = new ArgumentListExpression(new ConstantExpression(path), creator);
        MethodCallExpression methodCallExpression = new MethodCallExpression(subject, "addCreator", arguments);
        generatedStatements.add(new ExpressionStatement(methodCallExpression));
    }

    public List<Statement> getGeneratedStatements() {
        return generatedStatements;
    }
}
