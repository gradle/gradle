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

package org.gradle.model.dsl.internal.transform;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.Nullable;
import org.gradle.groovy.scripts.internal.AstUtils;
import org.gradle.groovy.scripts.internal.RestrictiveCodeVisitor;
import org.gradle.internal.Pair;
import org.gradle.model.internal.core.ModelPath;

import java.util.List;

public class RulesVisitor extends RestrictiveCodeVisitor {

    private static final String AST_NODE_METADATA_KEY = RulesVisitor.class.getName();
    private static final ClassNode ANNOTATION_CLASS_NODE = new ClassNode(RulesBlock.class);

    // TODO - have to do much better here
    public static final String INVALID_STATEMENT = "illegal rule";
    public static final String INVALID_RULE_SIGNATURE = "Rule must follow the pattern '«name»(«type») {}' for a registration, and '«name» {}' for an action";

    private final RuleVisitor ruleVisitor;

    public RulesVisitor(SourceUnit sourceUnit, RuleVisitor ruleVisitor) {
        super(sourceUnit, INVALID_STATEMENT);
        this.ruleVisitor = ruleVisitor;
    }

    public static void visitGeneratedClosure(ClassNode node) {
        MethodNode method = AstUtils.getGeneratedClosureImplMethod(node);
        Boolean isRulesBlock = method.getCode().getNodeMetaData(AST_NODE_METADATA_KEY);
        if (isRulesBlock != null) {
            AnnotationNode markerAnnotation = new AnnotationNode(ANNOTATION_CLASS_NODE);
            node.addAnnotation(markerAnnotation);
        }
    }

    @Override
    public void visitBlockStatement(BlockStatement block) {
        block.setNodeMetaData(AST_NODE_METADATA_KEY, true);

        for (Statement statement : block.getStatements()) {
            statement.visit(this);
        }
    }

    @Override
    public void visitExpressionStatement(ExpressionStatement statement) {
        statement.getExpression().visit(this);
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        ClosureExpression closureExpression = AstUtils.getSingleClosureArg(call);
        if (closureExpression != null) {
            // path { ... }
            rewriteAction(call, extractModelPathFromMethodTarget(call), closureExpression, RuleVisitor.displayName(call));
            return;
        }

        Pair<ClassExpression, ClosureExpression> args = AstUtils.getClassAndClosureArgs(call);
        if (args != null) {
            // path(Type) { ... }
            rewriteCreator(call, extractModelPathFromMethodTarget(call), args.getRight(), args.getLeft(), RuleVisitor.displayName(call));
            return;
        }

        ClassExpression classArg = AstUtils.getClassArg(call);
        if (classArg != null) {
            // path(Type)
            String displayName = RuleVisitor.displayName(call);
            List<Statement> statements = Lists.newLinkedList();
            statements.add(new EmptyStatement());
            BlockStatement block = new BlockStatement(statements, new VariableScope());
            closureExpression = new ClosureExpression(Parameter.EMPTY_ARRAY, block);
            closureExpression.setVariableScope(block.getVariableScope());
            String modelPath = extractModelPathFromMethodTarget(call);
            rewriteCreator(call, modelPath, closureExpression, classArg, displayName);
            return;
        }

        restrict(call, INVALID_RULE_SIGNATURE);
    }

    public void rewriteCreator(MethodCallExpression call, String modelPath, ClosureExpression closureExpression, ClassExpression typeExpression, String displayName) {
        // Rewrite the method call to match TransformedModelDslBacking#create(String, Closure), which is what the delegate will be
        ConstantExpression modelPathArgument = new ConstantExpression(modelPath);
        ArgumentListExpression replacedArgumentList = new ArgumentListExpression(modelPathArgument, typeExpression, closureExpression);
        call.setMethod(new ConstantExpression("create"));
        call.setArguments(replacedArgumentList);

        // Call directly on the delegate to avoid some dynamic dispatch
        call.setImplicitThis(true);
        call.setObjectExpression(new MethodCallExpression(VariableExpression.THIS_EXPRESSION, "getDelegate", ArgumentListExpression.EMPTY_ARGUMENTS));

        ruleVisitor.visitRuleClosure(closureExpression, call, displayName);
    }

    public void rewriteAction(MethodCallExpression call, String modelPath, ClosureExpression closureExpression, String displayName) {
        // Rewrite the method call to match TransformedModelDslBacking#configure(String, Closure), which is what the delegate will be
        ConstantExpression modelPathArgument = new ConstantExpression(modelPath);
        ArgumentListExpression replacedArgumentList = new ArgumentListExpression(modelPathArgument, closureExpression);
        call.setMethod(new ConstantExpression("configure"));
        call.setArguments(replacedArgumentList);

        // Call directly on the delegate to avoid some dynamic dispatch
        call.setImplicitThis(true);
        call.setObjectExpression(new MethodCallExpression(VariableExpression.THIS_EXPRESSION, "getDelegate", ArgumentListExpression.EMPTY_ARGUMENTS));

        ruleVisitor.visitRuleClosure(closureExpression, call, displayName);
    }

    @Nullable // if the target was invalid
    private String extractModelPathFromMethodTarget(MethodCallExpression call) {
        Expression target = call.getMethod();
        List<String> names = Lists.newLinkedList();
        while (true) {
            if (target instanceof ConstantExpression) {
                if (target.getType().equals(ClassHelper.STRING_TYPE)) {
                    String name = target.getText();
                    names.add(0, name);
                    if (call.isImplicitThis()) {
                        break;
                    } else {
                        target = call.getObjectExpression();
                        continue;
                    }
                }
            } else if (target instanceof PropertyExpression) {
                PropertyExpression propertyExpression = (PropertyExpression) target;
                Expression property = propertyExpression.getProperty();
                if (property instanceof ConstantExpression) {
                    ConstantExpression constantProperty = (ConstantExpression) property;
                    if (constantProperty.getType().equals(ClassHelper.STRING_TYPE)) {
                        String name = constantProperty.getText();
                        names.add(0, name);
                        target = propertyExpression.getObjectExpression();
                        continue;
                    }
                }
            } else if (target instanceof VariableExpression) {
                // This will be the left most property
                names.add(0, ((VariableExpression) target).getName());
                break;
            }

            // Invalid paths fall through to here

            restrict(call);
            return null;
        }

        // TODO - validate that it's a valid model path
        return ModelPath.pathString(Iterables.toArray(names, String.class));
    }
}
