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
import org.gradle.model.internal.core.ModelPath;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@NotThreadSafe
public class RuleVisitor extends ExpressionReplacingVisitorSupport {

    public static final String INVALID_ARGUMENT_LIST = "argument list must be exactly 1 literal non empty string";

    private static final String AST_NODE_METADATA_INPUTS_KEY = RuleVisitor.class.getName() + ".inputs";
    private static final String AST_NODE_METADATA_LOCATION_KEY = RuleVisitor.class.getName() + ".location";

    private static final String DOLLAR = "$";
    private static final String GET = "get";
    private static final ClassNode RULE_METADATA = new ClassNode(RuleMetadata.class);
    private static final ClassNode POTENTIAL_INPUTS = new ClassNode(PotentialInputs.class);
    private static final ClassNode TRANSFORMED_CLOSURE = new ClassNode(TransformedClosure.class);

    private static final String INPUTS_LVAR_NAME = "__rule_inputs_var__";
    private static final String INPUTS_FIELD_NAME = "__rule_inputs__";
    private static final Token ASSIGN = new Token(Types.ASSIGN, "=", -1, -1);

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

            metadataAnnotation.addMember("absoluteScriptSourceLocation", new ConstantExpression(sourceLocation.getUri()));
            metadataAnnotation.addMember("lineNumber", new ConstantExpression(sourceLocation.getLineNumber()));
            metadataAnnotation.addMember("columnNumber", new ConstantExpression(sourceLocation.getColumnNumber()));

            InputReferences inputs = closureCode.getNodeMetaData(AST_NODE_METADATA_INPUTS_KEY);
            if (!inputs.isEmpty()) {
                metadataAnnotation.addMember("absoluteInputPaths", new ListExpression(constants(inputs.getAbsolutePaths())));
                metadataAnnotation.addMember("absoluteInputLineNumbers", new ListExpression(constants(inputs.getAbsolutePathLineNumbers())));
            }

            node.addAnnotation(metadataAnnotation);

            node.addInterface(TRANSFORMED_CLOSURE);
            node.addField(new FieldNode(INPUTS_FIELD_NAME, Modifier.PUBLIC, POTENTIAL_INPUTS, node, null));
            List<Statement> statements = new ArrayList<Statement>();
            statements.add(new ExpressionStatement(new BinaryExpression(new VariableExpression(INPUTS_FIELD_NAME), ASSIGN, new VariableExpression("inputs"))));
            node.addMethod(new MethodNode("applyRuleInputs", Modifier.PUBLIC, ClassHelper.VOID_TYPE,
                    new Parameter[]{new Parameter(POTENTIAL_INPUTS, "inputs")},
                    new ClassNode[0],
                    new BlockStatement(statements, new VariableScope())));
        }
    }

    private static List<Expression> constants(Collection<?> values) {
        List<Expression> expressions = Lists.newArrayListWithCapacity(values.size());
        for (Object value : values) {
            expressions.add(new ConstantExpression(value));
        }
        return expressions;
    }

    public void visitRuleClosure(ClosureExpression expression, SourceLocation sourceLocation) {
        if (inputs != null) {
            throw new IllegalStateException("Already visiting a rule closure");
        }
        inputs = new InputReferences();
        try {
            inputsVariable = new VariableExpression(INPUTS_LVAR_NAME, POTENTIAL_INPUTS);
            super.visitClosureExpression(expression);
            BlockStatement code = (BlockStatement) expression.getCode();
            code.setNodeMetaData(AST_NODE_METADATA_LOCATION_KEY, sourceLocation);
            code.setNodeMetaData(AST_NODE_METADATA_INPUTS_KEY, inputs);
            inputsVariable.setClosureSharedVariable(true);
            code.getVariableScope().putDeclaredVariable(inputsVariable);

            // <inputs-lvar> = <inputs-field>
            DeclarationExpression variableDeclaration = new DeclarationExpression(inputsVariable, ASSIGN, new VariableExpression(INPUTS_FIELD_NAME));
            code.getStatements().add(0, new ExpressionStatement(variableDeclaration));

            // Move default values into body of closure, so they can use <inputs-lvar>
            for (Parameter parameter : expression.getParameters()) {
                if (parameter.hasInitialExpression()) {
                    code.getStatements().add(1, new ExpressionStatement(new BinaryExpression(new VariableExpression(parameter.getName()), ASSIGN, parameter.getInitialExpression())));
                    parameter.setInitialExpression(ConstantExpression.NULL);
                }
            }
        } finally {
            inputs = null;
        }
    }

    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        // Nested closure
        expression.getVariableScope().putReferencedLocalVariable(inputsVariable);
        super.visitClosureExpression(expression);
    }

    @Override
    public void visitPropertyExpression(PropertyExpression expr) {
        String modelPath = isDollarPathExpression(expr);
        if (modelPath != null) {
            inputs.absolutePath(modelPath, expr.getLineNumber());
            replaceVisitedExpressionWith(inputReferenceExpression(modelPath));
        } else {
            super.visitPropertyExpression(expr);
        }
    }

    private String isDollarPathExpression(PropertyExpression expr) {
        if (expr.getObjectExpression() instanceof VariableExpression) {
            VariableExpression objectExpression = (VariableExpression) expr.getObjectExpression();
            if (objectExpression.getName().equals(DOLLAR)) {
                return expr.getPropertyAsString();
            } else {
                return null;
            }
        }
        if (expr.getObjectExpression() instanceof PropertyExpression) {
            PropertyExpression objectExpression = (PropertyExpression) expr.getObjectExpression();
            String path = isDollarPathExpression(objectExpression);
            if (path != null) {
                return path + '.' + expr.getPropertyAsString();
            } else {
                return null;
            }
        }
        return null;
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
            replaceVisitedExpressionWith(inputReferenceExpression(modelPath));
        }
    }

    private MethodCallExpression inputReferenceExpression(String modelPath) {
        return new MethodCallExpression(new VariableExpression(inputsVariable), new ConstantExpression(GET), new ArgumentListExpression(new ConstantExpression(modelPath)));
    }

    private void error(ASTNode call, String message) {
        SyntaxException syntaxException = new SyntaxException(message, call.getLineNumber(), call.getColumnNumber());
        sourceUnit.getErrorCollector().addError(syntaxException, sourceUnit);
    }
}
