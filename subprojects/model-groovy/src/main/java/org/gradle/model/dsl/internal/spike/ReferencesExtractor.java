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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.util.CollectionUtils;

import java.util.LinkedList;

public class ReferencesExtractor extends BlockAndExpressionStatementAllowingRestrictiveCodeVisitor {

    private final static String AST_NODE_REWRITE_KEY = ReferencesExtractor.class.getName();

    private boolean referenceEncountered;
    private LinkedList<String> referenceStack = Lists.newLinkedList();
    private ImmutableSet.Builder<String> referencedPaths = ImmutableSet.builder();

    public ReferencesExtractor(SourceUnit sourceUnit) {
        super(sourceUnit, "Expression not allowed");
    }

    @Override
    public void visitVariableExpression(VariableExpression expression) {
        if (expression.getName().equals("$")) {
            referenceEncountered = true;
        }
    }

    @Override
    public void visitExpressionStatement(ExpressionStatement statement) {
        super.visitExpressionStatement(statement);
        Expression rewritten = statement.getExpression().getNodeMetaData(AST_NODE_REWRITE_KEY);
        if (rewritten != null) {
            statement.setExpression(rewritten);
        }
    }

    @Override
    public void visitConstantExpression(ConstantExpression expression) {
        //allow this kind of expressions
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        //allow this kind of expressions for now
    }

    public void visitPropertyExpression(PropertyExpression expression) {
        boolean topLevel = referenceStack.isEmpty();
        referenceStack.push(expression.getPropertyAsString());
        expression.getObjectExpression().visit(this);
        if (topLevel) {
            if (referenceEncountered) {
                String path = CollectionUtils.join(".", referenceStack);
                referencedPaths.add(path);
                expression.setNodeMetaData(AST_NODE_REWRITE_KEY, rewriteReferenceStatement(path));
                referenceStack.clear();
            }
            referenceEncountered = false;
        }
    }

    private MethodCallExpression rewriteReferenceStatement(String path) {
        Parameter it = new Parameter(ClassHelper.DYNAMIC_TYPE, "it");
        it.setOriginType(ClassHelper.OBJECT_TYPE);
        VariableExpression subject = new VariableExpression(it);
        ArgumentListExpression arguments = new ArgumentListExpression(new ConstantExpression(path));
        return new MethodCallExpression(subject, "getAt", arguments);
    }

    public ImmutableSet<String> getReferencedPaths() {
        return referencedPaths.build();
    }
}
