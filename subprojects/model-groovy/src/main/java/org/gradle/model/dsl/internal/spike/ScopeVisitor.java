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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.gradle.groovy.scripts.internal.AstUtils;
import org.gradle.util.CollectionUtils;

import java.util.List;
import java.util.Map;

public class ScopeVisitor extends ReferenceDetectingVisitor {

    private final ModelRegistryDslHelperStatementGenerator statementGenerator;
    private final List<String> scope;
    private final SourceUnit sourceUnit;
    private final Map<String, String> referenceAliases = Maps.newHashMap();

    public ScopeVisitor(SourceUnit sourceUnit, ModelRegistryDslHelperStatementGenerator statementGenerator) {
        this(sourceUnit, statementGenerator, ImmutableList.<String>of(), ImmutableMap.<String, String>of());
    }

    private ScopeVisitor(SourceUnit sourceUnit, ModelRegistryDslHelperStatementGenerator statementGenerator, List<String> scope, Map<String, String> referenceAliases) {
        super(sourceUnit, "Expression not allowed");
        this.statementGenerator = statementGenerator;
        this.scope = scope;
        this.sourceUnit = sourceUnit;
        this.referenceAliases.putAll(referenceAliases);
    }

    private ScopeVisitor nestedScope(String name) {
        ImmutableList.Builder<String> nestedScopeBuilder = ImmutableList.builder();
        nestedScopeBuilder.addAll(scope);
        nestedScopeBuilder.add(name);
        return new ScopeVisitor(sourceUnit, statementGenerator, nestedScopeBuilder.build(), ImmutableMap.copyOf(referenceAliases));
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        Expression rightExpression = expression.getRightExpression();
        rightExpression.visit(this);

        String referencePath = rightExpression.getNodeMetaData(AST_NODE_REFERENCE_PATH_KEY);
        if (referencePath != null) {
            referenceAliases.put(expression.getLeftExpression().getText(), referencePath);
        }
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        String methodName = AstUtils.extractConstantMethodName(call);
        if (methodName != null) {
            ClosureExpression nestedAction = AstUtils.getSingleClosureArg(call);
            if (nestedAction != null) {
                nestedAction.getCode().visit(nestedScope(methodName));
                return;
            }
        }
        super.visitMethodCallExpression(call);
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        Token operation = expression.getOperation();
        if (operation.isA(Types.LEFT_SHIFT) && expression.getLeftExpression() instanceof VariableExpression && expression.getRightExpression() instanceof ClosureExpression) {
            addCreator(scope, (VariableExpression) expression.getLeftExpression(), (ClosureExpression) expression.getRightExpression());
        } else if (operation.isA(Types.ASSIGN)) {
            if (expression.getLeftExpression() instanceof VariableExpression) {
                addCreator(scope, (VariableExpression) expression.getLeftExpression(), expression.getRightExpression());
            } else if (expression.getLeftExpression() instanceof PropertyExpression) {
                addCreator(scope, (PropertyExpression) expression.getLeftExpression(), expression.getRightExpression());
            } else {
                super.visitBinaryExpression(expression);
            }
        } else {
            super.visitBinaryExpression(expression);
        }
    }

    private String getPath(List<String> scope, Expression propertyPathExpression) {
        String propertyPath = propertyPathExpression.getText();
        String scopePath = CollectionUtils.join(".", scope);
        return scopePath.length() > 0 ? String.format("%s.%s", scopePath, propertyPath) : propertyPath;
    }

    private void addCreator(String path, ClosureExpression creator) {
        final ImmutableMap<String, String> referenceAliasesMap = ImmutableMap.copyOf(referenceAliases);
        ReferenceExtractor extractor = new ReferenceExtractor(sourceUnit, referenceAliasesMap);
        Iterators.removeIf(creator.getVariableScope().getReferencedLocalVariablesIterator(), new Predicate<Variable>() {
            public boolean apply(Variable variable) {
                return referenceAliasesMap.keySet().contains(variable.getName());
            }
        });
        creator.getCode().visit(extractor);
        statementGenerator.addCreator(path, creator, extractor.getReferencedPaths());
    }

    private void addCreator(List<String> scope, VariableExpression propertyPathExpression, ClosureExpression creator) {
        addCreator(getPath(scope, propertyPathExpression), creator);
    }

    private void addCreator(String path, Expression expression) {
        ClosureExpression wrappingClosure = new ClosureExpression(new Parameter[0], new ExpressionStatement(expression));
        wrappingClosure.setVariableScope(new VariableScope());
        addCreator(path, wrappingClosure);
    }

    public void addCreator(List<String> scope, VariableExpression propertyPathExpression, Expression expression) {
        addCreator(getPath(scope, propertyPathExpression), expression);
    }

    public void addCreator(List<String> scope, PropertyExpression propertyPathExpression, Expression expression) {
        addCreator(getPath(scope, propertyPathExpression), expression);
    }

    @Override
    protected String getReferenceAliasPath(String aliasName) {
        return referenceAliases.get(aliasName);
    }
}
