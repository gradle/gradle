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

import com.google.common.collect.Lists;
import net.jcip.annotations.NotThreadSafe;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.gradle.groovy.scripts.internal.AstUtils;
import org.gradle.groovy.scripts.internal.ExpressionReplacingVisitorSupport;
import org.gradle.internal.SystemProperties;
import org.gradle.model.dsl.internal.inputs.RuleInputAccess;
import org.gradle.model.dsl.internal.inputs.RuleInputAccessBacking;
import org.gradle.model.internal.core.ModelPath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@NotThreadSafe
public class RuleVisitor extends ExpressionReplacingVisitorSupport {

    public static final String INVALID_ARGUMENT_LIST = "argument list must be exactly 1 literal non empty string";

    private static final String AST_NODE_METADATA_INPUTS_KEY = RuleVisitor.class.getName() + ".inputs";
    public static final String AST_NODE_METADATA_LOCATION_KEY = RuleVisitor.class.getName() + ".location";

    private static final String DOLLAR = "$";
    private static final String INPUT = "input";
    private static final String HAS = "has";
    private static final ClassNode ANNOTATION_CLASS_NODE = new ClassNode(RuleMetadata.class);
    private static final ClassNode CONTEXTUAL_INPUT_TYPE = new ClassNode(RuleInputAccessBacking.class);
    private static final ClassNode ACCESS_API_TYPE = new ClassNode(RuleInputAccess.class);
    private static final String GET_ACCESS = "getAccess";

    private static final String ACCESS_HOLDER_FIELD = "_" + RuleInputAccess.class.getName().replace(".", "_");

    private final SourceUnit sourceUnit;
    private InputReferences inputs;
    private VariableExpression accessVariable;

    public RuleVisitor(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
    }

    // Not part of a normal visitor, see ClosureCreationInterceptingVerifier
    public static void visitGeneratedClosure(ClassNode node) {
        MethodNode method = AstUtils.getGeneratedClosureImplMethod(node);
        Statement closureCode = method.getCode();
        SourceLocation sourceLocation = closureCode.getNodeMetaData(AST_NODE_METADATA_LOCATION_KEY);
        if (sourceLocation != null) {
            AnnotationNode metadataAnnotation = new AnnotationNode(ANNOTATION_CLASS_NODE);


            metadataAnnotation.addMember("scriptSourceDescription", new ConstantExpression(sourceLocation.getScriptSourceDescription()));
            metadataAnnotation.addMember("absoluteScriptSourceLocation", new ConstantExpression(sourceLocation.getUri()));
            metadataAnnotation.addMember("lineNumber", new ConstantExpression(sourceLocation.getLineNumber()));
            metadataAnnotation.addMember("columnNumber", new ConstantExpression(sourceLocation.getColumnNumber()));

            InputReferences inputs = closureCode.getNodeMetaData(AST_NODE_METADATA_INPUTS_KEY);
            if (!inputs.isEmpty()) {
                metadataAnnotation.addMember("absoluteInputPaths", new ListExpression(constants(inputs.getAbsolutePaths())));
                metadataAnnotation.addMember("absoluteInputLineNumbers", new ListExpression(constants(inputs.getAbsolutePathLineNumbers())));
                metadataAnnotation.addMember("relativeInputPaths", new ListExpression(constants(inputs.getRelativePaths())));
                metadataAnnotation.addMember("relativeInputLineNumbers", new ListExpression(constants(inputs.getRelativePathLineNumbers())));
            }

            node.addAnnotation(metadataAnnotation);
        }
    }

    private static List<Expression> constants(Collection<?> values) {
        List<Expression> expressions = Lists.newArrayListWithCapacity(values.size());
        for (Object value : values) {
            expressions.add(new ConstantExpression(value));
        }
        return expressions;
    }

    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        if (inputs == null) {
            inputs = new InputReferences();
            try {
                accessVariable = new VariableExpression(ACCESS_HOLDER_FIELD, ACCESS_API_TYPE);

                super.visitClosureExpression(expression);

                BlockStatement code = (BlockStatement) expression.getCode();
                code.setNodeMetaData(AST_NODE_METADATA_INPUTS_KEY, inputs);
                accessVariable.setClosureSharedVariable(true);
                StaticMethodCallExpression getAccessCall = new StaticMethodCallExpression(CONTEXTUAL_INPUT_TYPE, GET_ACCESS, ArgumentListExpression.EMPTY_ARGUMENTS);
                DeclarationExpression variableDeclaration = new DeclarationExpression(accessVariable, new Token(Types.ASSIGN, "=", -1, -1), getAccessCall);
                code.getStatements().add(0, new ExpressionStatement(variableDeclaration));
                code.getVariableScope().putDeclaredVariable(accessVariable);
            } finally {
                inputs = null;
            }
        } else {
            expression.getVariableScope().putReferencedLocalVariable(accessVariable);
            super.visitClosureExpression(expression);
        }
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        String methodName = call.getMethodAsString();
        if (call.isImplicitThis() && methodName != null && methodName.equals(DOLLAR)) {
            visitInputMethod(call);
        } else {
            // visit the method call, because one of the args may be an input method call
            super.visitMethodCallExpression(call);
        }
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        if (expression.getLeftExpression() instanceof VariableExpression) {
            expression.setRightExpression(replaceExpr(expression.getRightExpression()));
        } else {
            super.visitBinaryExpression(expression);
        }
    }

    @Override
    public void visitPropertyExpression(PropertyExpression expression) {
        ArrayList<String> names = Lists.newArrayList();
        boolean propertyNameIsPart = extractPropertyPath(expression, names);
        if (names.isEmpty() || !names.get(0).equals("thing")) {
            super.visitPropertyExpression(expression);
        } else {
            String modelPath = ModelPath.pathString(names);
            inputs.relativePath(modelPath, expression.getLineNumber());
            if (propertyNameIsPart) {
                replaceVisitedExpressionWith(conditionalInputGet(modelPath, expression));
            } else {
                expression.setObjectExpression(conditionalInputGet(modelPath, expression.getObjectExpression()));
            }
        }
    }

    private TernaryExpression conditionalInputGet(String modelPath, Expression originalExpression) {
        return new TernaryExpression(
            new BooleanExpression(new MethodCallExpression(accessVariable, HAS, new ArgumentListExpression(new ConstantExpression(modelPath)))),
            new MethodCallExpression(accessVariable, INPUT, new ArgumentListExpression(new ConstantExpression(modelPath))),
            originalExpression
        );
    }

    private boolean extractPropertyPath(Expression expression, List<String> names) {
        if (expression instanceof PropertyExpression) {
            PropertyExpression propertyExpression = (PropertyExpression) expression;
            if (extractPropertyPath(propertyExpression.getObjectExpression(), names)) {
                return extractPropertyPath(propertyExpression.getProperty(), names);
            }
        } else if (expression instanceof VariableExpression) {
            names.add(((VariableExpression) expression).getName());
        } else if (expression instanceof ConstantExpression) {
            ConstantExpression constantExpression = (ConstantExpression) expression;
            if (constantExpression.getType().equals(ClassHelper.STRING_TYPE)) {
                names.add(constantExpression.getText());
            } else {
                return false;
            }
        } else {
            return false;
        }

        return true;
    }

    @Override
    public void visitVariableExpression(VariableExpression expression) {
        if (expression.isThisExpression() || expression.isSuperExpression()) {
            super.visitVariableExpression(expression);
        } else {
            String modelPath = expression.getText();
            if (modelPath.equals("thing")) {
                inputs.relativePath(modelPath, expression.getLineNumber());
                replaceVisitedExpressionWith(conditionalInputGet(modelPath, expression));
            } else {
                super.visitVariableExpression(expression);
            }
        }
    }

    private void visitInputMethod(MethodCallExpression call) {
        ConstantExpression argExpression = AstUtils.hasSingleConstantStringArg(call);
        if (argExpression == null) { // not a valid signature
            error(call, INVALID_ARGUMENT_LIST);
        } else {
            String modelPath = argExpression.getText();
            if (modelPath.isEmpty()) {
                error(argExpression, INVALID_ARGUMENT_LIST);
                return;
            }

            try {
                ModelPath.validatePath(modelPath);
            } catch (ModelPath.InvalidPathException e) {
                // TODO find a better way to present this information in the error message
                // Attempt to mimic Gradle nested exception output
                String message = "Invalid model path given as rule input." + SystemProperties.getInstance().getLineSeparator()
                    + "  > " + e.getMessage();
                if (e.getCause() != null) {
                    // if there is a cause, it's an invalid name exception
                    message += SystemProperties.getInstance().getLineSeparator() + "    > " + e.getCause().getMessage();
                }
                error(argExpression, message);
                return;
            }

            inputs.absolutePath(modelPath, call.getLineNumber());
            call.setObjectExpression(new VariableExpression(accessVariable));
            call.setMethod(new ConstantExpression(INPUT));
        }
    }

    private void error(ASTNode call, String message) {
        SyntaxException syntaxException = new SyntaxException(message, call.getLineNumber(), call.getColumnNumber());
        sourceUnit.getErrorCollector().addError(syntaxException, sourceUnit);
    }

}
