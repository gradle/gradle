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
import org.gradle.model.dsl.internal.inputs.PotentialInputs;
import org.gradle.model.dsl.internal.inputs.PotentialInputsAccess;
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
    private static final String HAS = "has";
    private static final String GET = "get";
    private static final ClassNode RULE_METADATA = new ClassNode(RuleMetadata.class);
    private static final ClassNode POTENTIAL_INPUTS_ACCESS = new ClassNode(PotentialInputsAccess.class);
    private static final ClassNode POTENTIAL_INPUTS = new ClassNode(PotentialInputs.class);

    private static final String ACCESS_HOLDER_FIELD = "_" + PotentialInputs.class.getName().replace(".", "_");

    boolean isLeftSideOfAssignment;
    int closureDepth;

    private final SourceUnit sourceUnit;
    private InputReferences inputs;
    private VariableExpression inputsVariable;

    public RuleVisitor(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
    }

    // Not part of a normal visitor, see ClosureCreationInterceptingVerifier
    public static void visitGeneratedClosure(ClassNode node) {
        MethodNode method = AstUtils.getGeneratedClosureImplMethod(node);
        Statement closureCode = method.getCode();
        SourceLocation sourceLocation = closureCode.getNodeMetaData(AST_NODE_METADATA_LOCATION_KEY);
        if (sourceLocation != null) {
            AnnotationNode metadataAnnotation = new AnnotationNode(RULE_METADATA);

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
    public void visitExpressionStatement(ExpressionStatement stat) {
        if (!(stat.getExpression() instanceof VariableExpression)) {
            super.visitExpressionStatement(stat);
        }
    }

    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        if (inputs == null) {
            inputs = new InputReferences();
            try {
                inputsVariable = new VariableExpression(ACCESS_HOLDER_FIELD, POTENTIAL_INPUTS);
                super.visitClosureExpression(expression);
                BlockStatement code = (BlockStatement) expression.getCode();
                code.setNodeMetaData(AST_NODE_METADATA_INPUTS_KEY, inputs);
                inputsVariable.setClosureSharedVariable(true);
                StaticMethodCallExpression getAccessCall = new StaticMethodCallExpression(POTENTIAL_INPUTS_ACCESS, GET, ArgumentListExpression.EMPTY_ARGUMENTS);
                DeclarationExpression variableDeclaration = new DeclarationExpression(inputsVariable, new Token(Types.ASSIGN, "=", -1, -1), getAccessCall);
                code.getStatements().add(0, new ExpressionStatement(variableDeclaration));
                code.getVariableScope().putDeclaredVariable(inputsVariable);
            } finally {
                inputs = null;
            }
        } else {
            ++closureDepth;
            try {
                expression.getVariableScope().putReferencedLocalVariable(inputsVariable);
                super.visitClosureExpression(expression);
            } finally {
                --closureDepth;
            }
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
        if (atTopLevel() && isAssignmentLikeToken(expression.getOperation().getMeaning())) {
            isLeftSideOfAssignment = true;
            try {
                expression.setLeftExpression(replaceExpr(expression.getLeftExpression()));
            } finally {
                isLeftSideOfAssignment = false;
            }
            expression.setRightExpression(replaceExpr(expression.getRightExpression()));
        } else {
            super.visitBinaryExpression(expression);
        }
    }

    private boolean atTopLevel() {
        return closureDepth == 0;
    }

    @Override
    public void visitPropertyExpression(PropertyExpression expression) {
        if (atTopLevel()) {
            ArrayList<String> names = Lists.newArrayList();
            boolean propertyNameIsPart = extractPropertyPath(expression, names);
            if (!names.isEmpty()) {
                String modelPath = ModelPath.pathString(names);
                inputs.relativePath(modelPath, expression.getLineNumber());
                if (propertyNameIsPart) {
                    replaceVisitedExpressionWith(conditionalInputGet(modelPath, expression));
                } else {
                    expression.setObjectExpression(conditionalInputGet(modelPath, expression.getObjectExpression()));
                }
            }
        } else {
            super.visitPropertyExpression(expression);
        }
    }

    private TernaryExpression conditionalInputGet(String modelPath, Expression originalExpression) {
        return new TernaryExpression(
            new BooleanExpression(new MethodCallExpression(inputsVariable, HAS, new ArgumentListExpression(new ConstantExpression(modelPath)))),
            new MethodCallExpression(inputsVariable, GET, new ArgumentListExpression(new ConstantExpression(modelPath))),
            originalExpression
        );
    }

    private boolean extractPropertyPath(Expression expression, List<String> names) {
        if (expression instanceof PropertyExpression) {
            PropertyExpression propertyExpression = (PropertyExpression) expression;
            //noinspection SimplifiableIfStatement
            if (extractPropertyPath(propertyExpression.getObjectExpression(), names)) {
                return extractPropertyPath(propertyExpression.getProperty(), names);
            }
        } else if (expression instanceof MethodCallExpression) {
            visitMethodCallExpression((MethodCallExpression) expression);
            return false;
        } else if (!isLeftSideOfAssignment) {
            if (expression instanceof VariableExpression) {
                VariableExpression variableExpression = (VariableExpression) expression;
                if (!isNonReferenceKeyword(variableExpression)) {
                    Variable accessedVariable = variableExpression.getAccessedVariable();
                    if (accessedVariable instanceof DynamicVariable) {
                        names.add(variableExpression.getName());
                        return true;
                    }
                }
            } else if (expression instanceof ConstantExpression) {
                ConstantExpression constantExpression = (ConstantExpression) expression;
                if (constantExpression.getType().equals(ClassHelper.STRING_TYPE)) {
                    names.add(constantExpression.getText());
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isNonReferenceKeyword(VariableExpression expression) {
        return expression.isThisExpression()
            || expression.isSuperExpression()
            || expression.getText().equals("owner")
            || expression.getText().equals("delegate")
            || expression.getText().equals("it");
    }

    @Override
    public void visitVariableExpression(VariableExpression expression) {
        if (!atTopLevel() || isLeftSideOfAssignment || isNonReferenceKeyword(expression) || !(expression.getAccessedVariable() instanceof DynamicVariable)) {
            super.visitVariableExpression(expression);
        } else {
            String modelPath = expression.getText();
            inputs.relativePath(modelPath, expression.getLineNumber());
            replaceVisitedExpressionWith(conditionalInputGet(modelPath, expression));
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
            call.setObjectExpression(new VariableExpression(inputsVariable));
            call.setMethod(new ConstantExpression(GET));
        }
    }

    private void error(ASTNode call, String message) {
        SyntaxException syntaxException = new SyntaxException(message, call.getLineNumber(), call.getColumnNumber());
        sourceUnit.getErrorCollector().addError(syntaxException, sourceUnit);
    }

    private static final int[] ASSIGNMENT_TOKENS = new int[]{
        Types.EQUAL,
        Types.PLUS_EQUAL,
        Types.MINUS_EQUAL,
        Types.MULTIPLY_EQUAL,
        Types.DIVIDE_EQUAL,
        Types.INTDIV_EQUAL,
        Types.MOD_EQUAL,
        Types.POWER_EQUAL,
        Types.LEFT_SHIFT_EQUAL,
        Types.RIGHT_SHIFT_EQUAL,
        Types.RIGHT_SHIFT_UNSIGNED_EQUAL,
        Types.BITWISE_OR_EQUAL,
        Types.BITWISE_AND_EQUAL,
        Types.BITWISE_XOR_EQUAL,
    };

    private boolean isAssignmentLikeToken(int token) {
        for (int assignmentToken : ASSIGNMENT_TOKENS) {
            if (assignmentToken == token) {
                return true;
            }
        }

        return false;
    }
}
