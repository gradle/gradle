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
import com.google.common.collect.Maps;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.control.SourceUnit;

import java.util.Map;

public class ReferenceExtractor extends ReferenceDetectingVisitor {

    private final static String AST_NODE_REMOVE_KEY = ReferenceExtractor.class.getName() + ".remove";
    private final Map<String, String> outerScopeReferenceAliases;

    private ImmutableSet.Builder<String> referencedPaths = ImmutableSet.builder();
    private Map<String, String> referenceAliases = Maps.newHashMap();

    public ReferenceExtractor(SourceUnit sourceUnit, Map<String, String> outerScopeReferenceAliases) {
        super(sourceUnit, "Expression not allowed");
        this.outerScopeReferenceAliases = outerScopeReferenceAliases;
    }

    private Expression rewrittenOrOriginal(Expression expression) {
        String referencePath = expression.getNodeMetaData(AST_NODE_REFERENCE_PATH_KEY);

        return referencePath != null ? rewriteReferenceStatement(referencePath) : expression;
    }

    @Override
    public void visitExpressionStatement(ExpressionStatement statement) {
        super.visitExpressionStatement(statement);
        Expression expression = statement.getExpression();
        Boolean shouldRemoveExpression = expression.getNodeMetaData(AST_NODE_REMOVE_KEY);
        if (shouldRemoveExpression != null && shouldRemoveExpression) {
            statement.setExpression(new EmptyExpression());
        } else {
            statement.setExpression(rewrittenOrOriginal(expression));
        }
    }

    @Override
    public void visitConstantExpression(ConstantExpression expression) {
        //allow this kind of expressions
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        Expression leftExpression = expression.getLeftExpression();
        leftExpression.visit(this);
        expression.setLeftExpression(rewrittenOrOriginal(leftExpression));

        Expression rightExpression = expression.getRightExpression();
        rightExpression.visit(this);
        expression.setRightExpression(rewrittenOrOriginal(rightExpression));
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        Expression objectExpression = call.getObjectExpression();
        objectExpression.visit(this);
        call.setObjectExpression(rewrittenOrOriginal(objectExpression));
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        Expression rightExpression = expression.getRightExpression();
        rightExpression.visit(this);

        String referencePath = rightExpression.getNodeMetaData(AST_NODE_REFERENCE_PATH_KEY);
        if (referencePath != null) {
            expression.setNodeMetaData(AST_NODE_REMOVE_KEY, true);
            referenceAliases.put(expression.getLeftExpression().getText(), referencePath);
        }
    }

    private MethodCallExpression rewriteReferenceStatement(String path) {
        referencedPaths.add(path);

        Parameter it = new Parameter(ClassHelper.DYNAMIC_TYPE, "it");
        it.setOriginType(ClassHelper.OBJECT_TYPE);
        VariableExpression subject = new VariableExpression(it);
        ArgumentListExpression arguments = new ArgumentListExpression(new ConstantExpression(path));
        return new MethodCallExpression(subject, "getAt", arguments);
    }

    @Override
    protected String getReferenceAliasPath(String aliasName) {
        String path = referenceAliases.get(aliasName);
        return path != null ? path : outerScopeReferenceAliases.get(aliasName);
    }

    public ImmutableSet<String> getReferencedPaths() {
        return referencedPaths.build();
    }
}
