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

import com.google.common.collect.ImmutableList;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.gradle.groovy.scripts.internal.RestrictiveCodeVisitor;

public class ScopeVisitor extends RestrictiveCodeVisitor {

    private final ModelRegistryDslHelperStatementGenerator statementGenerator;
    private final ImmutableList<String> scope;

    public ScopeVisitor(SourceUnit sourceUnit, ModelRegistryDslHelperStatementGenerator statementGenerator) {
        this(sourceUnit, statementGenerator, ImmutableList.<String>of());
    }

    private ScopeVisitor(SourceUnit sourceUnit, ModelRegistryDslHelperStatementGenerator statementGenerator, ImmutableList<String> scope) {
        super(sourceUnit, "Expression not allowed");
        this.statementGenerator = statementGenerator;
        this.scope = scope;
    }

    public void visitBlockStatement(BlockStatement block) {
        for (Statement statement : block.getStatements()) {
            statement.visit(this);
        }
    }

    public void visitExpressionStatement(ExpressionStatement statement) {
        statement.getExpression().visit(this);
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        Token operation = expression.getOperation();
        if (operation.isA(Types.LEFT_SHIFT) && expression.getLeftExpression() instanceof VariableExpression && expression.getRightExpression() instanceof ClosureExpression) {
            statementGenerator.addCreator(scope, (VariableExpression) expression.getLeftExpression(), (ClosureExpression) expression.getRightExpression());
        } else if (operation.isA(Types.ASSIGN) && expression.getLeftExpression() instanceof VariableExpression) {
            statementGenerator.addCreator(scope, (VariableExpression) expression.getLeftExpression(), expression.getRightExpression());
        } else {
            super.visitBinaryExpression(expression);
        }
    }
}
