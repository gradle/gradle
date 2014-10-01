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
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.Action;
import org.gradle.groovy.scripts.internal.RestrictiveCodeVisitor;
import org.gradle.util.CollectionUtils;

import java.util.LinkedList;

abstract public class ReferenceDetectingVisitor extends RestrictiveCodeVisitor {

    protected final static String AST_NODE_REFERENCE_PATH_KEY = ReferenceDetectingVisitor.class.getName() + ".referenceKey";

    protected boolean referenceEncountered;
    protected LinkedList<String> referenceStack = Lists.newLinkedList();

    public ReferenceDetectingVisitor(SourceUnit sourceUnit, String message) {
        super(sourceUnit, message);
    }

    public void visitBlockStatement(BlockStatement block) {
        for (Statement statement : block.getStatements()) {
            statement.visit(this);
        }
    }

    public void visitExpressionStatement(ExpressionStatement statement) {
        statement.getExpression().visit(this);
    }

    private <T extends Expression> void extractReferencePath(T expression, Action<T> action) {
        boolean topLevel = referenceStack.isEmpty();
        action.execute(expression);
        if (topLevel) {
            if (referenceEncountered) {
                String path = CollectionUtils.join(".", referenceStack);
                expression.setNodeMetaData(AST_NODE_REFERENCE_PATH_KEY, path);
                referenceEncountered = false;
            }
            referenceStack.clear();
        }
    }

    public void visitPropertyExpression(final PropertyExpression expression) {
        extractReferencePath(expression, new Action<PropertyExpression>() {
            public void execute(PropertyExpression propertyExpression) {
                referenceStack.push(expression.getPropertyAsString());
                expression.getObjectExpression().visit(ReferenceDetectingVisitor.this);
            }
        });
    }

    @Override
    public void visitVariableExpression(VariableExpression expression) {
        extractReferencePath(expression, new Action<VariableExpression>() {
            public void execute(VariableExpression expression) {
                String name = expression.getName();
                if (name.equals("$")) {
                    referenceEncountered = true;
                } else {
                    String path = getReferenceAliasPath(name);
                    if (path != null) {
                        referenceStack.push(path);
                        referenceEncountered = true;
                    }
                }
            }
        });
    }

    protected abstract String getReferenceAliasPath(String aliasName);
}
