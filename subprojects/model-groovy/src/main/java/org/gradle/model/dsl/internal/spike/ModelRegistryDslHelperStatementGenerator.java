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
import org.gradle.api.Transformer;
import org.gradle.util.CollectionUtils;

import java.util.List;
import java.util.Set;

class ModelRegistryDslHelperStatementGenerator {

    private final List<Statement> generatedStatements = Lists.newArrayList();

    public void addCreator(String path, ClosureExpression creator, Set<String> referencedPaths) {
        VariableExpression subject = new VariableExpression("modelRegistryHelper");
        List<Expression> pathValueExpressions = CollectionUtils.collect((Iterable<String>)referencedPaths, new Transformer<Expression, String>() {
            public Expression transform(String s) {
                return new ConstantExpression(s);
            }
        });

        ArgumentListExpression arguments = new ArgumentListExpression(new ConstantExpression(path), creator, new ListExpression(pathValueExpressions));
        MethodCallExpression methodCallExpression = new MethodCallExpression(subject, "addCreator", arguments);
        generatedStatements.add(new ExpressionStatement(methodCallExpression));
    }

    public List<Statement> getGeneratedStatements() {
        return generatedStatements;
    }
}
