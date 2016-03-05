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

import com.google.common.base.Joiner;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.gradle.api.Transformer;
import org.gradle.groovy.scripts.internal.AstUtils;
import org.gradle.groovy.scripts.internal.ExpressionReplacingVisitorSupport;
import org.gradle.internal.SystemProperties;
import org.gradle.model.dsl.internal.inputs.PotentialInputs;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RuleVisitor extends ExpressionReplacingVisitorSupport {

    public static final String INVALID_ARGUMENT_LIST = "argument list must be exactly 1 literal non empty string";
    public static final String SOURCE_URI_TOKEN = "@@sourceuri@@";
    public static final String SOURCE_DESC_TOKEN = "@@sourcedesc@@";

    private static final String AST_NODE_METADATA_INPUTS_KEY = RuleVisitor.class.getName() + ".inputs";
    private static final String AST_NODE_METADATA_LOCATION_KEY = RuleVisitor.class.getName() + ".location";

    private static final String DOLLAR = "$";
    private static final String GET = "get";
    private static final ClassNode POTENTIAL_INPUTS = new ClassNode(PotentialInputs.class);
    private static final ClassNode TRANSFORMED_CLOSURE = new ClassNode(TransformedClosure.class);
    private static final ClassNode INPUT_REFERENCES = new ClassNode(InputReferences.class);
    private static final ClassNode SOURCE_LOCATION = new ClassNode(SourceLocation.class);
    private static final ClassNode RULE_FACTORY = new ClassNode(ClosureBackedRuleFactory.class);

    private static final String INPUTS_FIELD_NAME = "__inputs__";
    private static final String RULE_FACTORY_FIELD_NAME = "__rule_factory__";
    private static final Token ASSIGN = new Token(Types.ASSIGN, "=", -1, -1);

    private final String scriptSourceDescription;
    private final URI location;
    private final SourceUnit sourceUnit;
    private InputReferences inputs;
    private VariableExpression inputsVariable;
    private int nestingDepth;
    private int counter;

    public RuleVisitor(SourceUnit sourceUnit, String scriptSourceDescription, URI location) {
        this.scriptSourceDescription = scriptSourceDescription;
        this.location = location;
        this.sourceUnit = sourceUnit;
    }

    // Not part of a normal visitor, see ClosureCreationInterceptingVerifier
    public static void visitGeneratedClosure(ClassNode node) {
        MethodNode closureCallMethod = AstUtils.getGeneratedClosureImplMethod(node);
        Statement closureCode = closureCallMethod.getCode();
        InputReferences inputs = closureCode.getNodeMetaData(AST_NODE_METADATA_INPUTS_KEY);
        if (inputs != null) {
            SourceLocation sourceLocation = closureCode.getNodeMetaData(AST_NODE_METADATA_LOCATION_KEY);
            node.addInterface(TRANSFORMED_CLOSURE);
            FieldNode inputsField = new FieldNode(INPUTS_FIELD_NAME, Modifier.PRIVATE, POTENTIAL_INPUTS, node, null);
            FieldNode ruleFactoryField = new FieldNode(RULE_FACTORY_FIELD_NAME, Modifier.PRIVATE, RULE_FACTORY, node, null);
            node.addField(inputsField);
            node.addField(ruleFactoryField);

            // Generate makeRule() method
            List<Statement> statements = new ArrayList<Statement>();
            statements.add(new ExpressionStatement(new BinaryExpression(new FieldExpression(inputsField), ASSIGN, new VariableExpression("inputs"))));
            statements.add(new ExpressionStatement(new BinaryExpression(new FieldExpression(ruleFactoryField), ASSIGN, new VariableExpression("ruleFactory"))));
            node.addMethod(new MethodNode("makeRule",
                    Modifier.PUBLIC,
                    ClassHelper.VOID_TYPE,
                    new Parameter[]{new Parameter(POTENTIAL_INPUTS, "inputs"), new Parameter(RULE_FACTORY, "ruleFactory")},
                    new ClassNode[0],
                    new BlockStatement(statements, new VariableScope())));

            // Generate inputReferences() method
            VariableExpression inputsVar = new VariableExpression("inputs", INPUT_REFERENCES);
            VariableScope methodVarScope = new VariableScope();
            methodVarScope.putDeclaredVariable(inputsVar);
            statements = new ArrayList<Statement>();
            statements.add(new ExpressionStatement(new DeclarationExpression(inputsVar, ASSIGN, new ConstructorCallExpression(INPUT_REFERENCES, new ArgumentListExpression()))));
            for (InputReference inputReference : inputs.getOwnReferences()) {
                statements.add(new ExpressionStatement(new MethodCallExpression(inputsVar,
                        "ownReference",
                        new ArgumentListExpression(
                                new ConstantExpression(inputReference.getPath()),
                                new ConstantExpression(inputReference.getLineNumber())))));
            }
            for (InputReference inputReference : inputs.getNestedReferences()) {
                statements.add(new ExpressionStatement(new MethodCallExpression(inputsVar,
                        "nestedReference",
                        new ArgumentListExpression(
                                new ConstantExpression(inputReference.getPath()),
                                new ConstantExpression(inputReference.getLineNumber())))));
            }
            statements.add(new ReturnStatement(inputsVar));
            node.addMethod(new MethodNode("inputReferences",
                                Modifier.PUBLIC,
                                INPUT_REFERENCES,
                                new Parameter[0],
                                new ClassNode[0],
                                new BlockStatement(statements, methodVarScope)));

            // Generate sourceLocation() method
            statements = new ArrayList<Statement>();
            statements.add(new ReturnStatement(new ConstructorCallExpression(SOURCE_LOCATION,
                    new ArgumentListExpression(Arrays.<Expression>asList(
                            new ConstantExpression(SOURCE_URI_TOKEN),
                            new ConstantExpression(SOURCE_DESC_TOKEN),
                            new ConstantExpression(sourceLocation.getExpression()),
                            new ConstantExpression(sourceLocation.getLineNumber()),
                            new ConstantExpression(sourceLocation.getColumnNumber())
                    )))));
            node.addMethod(new MethodNode("sourceLocation",
                                Modifier.PUBLIC,
                                SOURCE_LOCATION,
                                new Parameter[0],
                                new ClassNode[0],
                                new BlockStatement(statements, new VariableScope())));
        }
    }

    public void visitRuleClosure(ClosureExpression expression, Expression invocation, String invocationDisplayName) {
        InputReferences parentInputs = inputs;
        VariableExpression parentInputsVariable = inputsVariable;
        try {
            inputs = new InputReferences();
            inputsVariable = new VariableExpression("__rule_inputs_var_" + (counter++), POTENTIAL_INPUTS);
            inputsVariable.setClosureSharedVariable(true);
            super.visitClosureExpression(expression);
            BlockStatement code = (BlockStatement) expression.getCode();
            code.setNodeMetaData(AST_NODE_METADATA_LOCATION_KEY, new SourceLocation(location, scriptSourceDescription, invocationDisplayName, invocation.getLineNumber(), invocation.getColumnNumber()));
            code.setNodeMetaData(AST_NODE_METADATA_INPUTS_KEY, inputs);
            if (parentInputsVariable != null) {
                expression.getVariableScope().putReferencedLocalVariable(parentInputsVariable);
            }
            code.getVariableScope().putDeclaredVariable(inputsVariable);

            if (parentInputsVariable == null) {
                // <inputs-lvar> = <inputs-field>
                DeclarationExpression variableDeclaration = new DeclarationExpression(inputsVariable, ASSIGN, new VariableExpression(INPUTS_FIELD_NAME));
                code.getStatements().add(0, new ExpressionStatement(variableDeclaration));

            } else {
                // <inputs-lvar> = <inputs-field> ?: <parent-inputs-lvar>
                DeclarationExpression variableDeclaration = new DeclarationExpression(inputsVariable, ASSIGN,
                        new ElvisOperatorExpression(
                                new VariableExpression(INPUTS_FIELD_NAME),
                                parentInputsVariable));
                code.getStatements().add(0, new ExpressionStatement(variableDeclaration));
            }

            // Move default values into body of closure, so they can use <inputs-lvar>
            for (Parameter parameter : expression.getParameters()) {
                if (parameter.hasInitialExpression()) {
                    code.getStatements().add(1, new ExpressionStatement(new BinaryExpression(new VariableExpression(parameter.getName()), ASSIGN, parameter.getInitialExpression())));
                    parameter.setInitialExpression(ConstantExpression.NULL);
                }
            }
        } finally {
            if (parentInputs != null) {
                parentInputs.addNestedReferences(inputs);
            }
            inputs = parentInputs;
            inputsVariable = parentInputsVariable;
        }
    }

    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        // Nested closure
        nestingDepth++;
        try {
            expression.getVariableScope().putReferencedLocalVariable(inputsVariable);
            super.visitClosureExpression(expression);
        } finally {
            nestingDepth--;
        }
    }

    @Override
    public void visitPropertyExpression(PropertyExpression expr) {
        String modelPath = isDollarPathExpression(expr);
        if (modelPath != null) {
            inputs.ownReference(modelPath, expr.getLineNumber());
            replaceVisitedExpressionWith(inputReferenceExpression(modelPath));
        } else {
            super.visitPropertyExpression(expr);
        }
    }

    private String isDollarPathExpression(PropertyExpression expr) {
        if (expr.isSafe() || expr.isSpreadSafe()) {
            return null;
        }
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
    public void visitExpressionStatement(ExpressionStatement stat) {
        if (nestingDepth == 0 && stat.getExpression() instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) stat.getExpression();
            if (call.isImplicitThis() && call.getArguments() instanceof ArgumentListExpression) {
                ArgumentListExpression arguments = (ArgumentListExpression) call.getArguments();
                if (!arguments.getExpressions().isEmpty()) {
                    Expression lastArg = arguments.getExpression(arguments.getExpressions().size() - 1);
                    if (lastArg instanceof ClosureExpression) {
                        // This is a potential nested rule.
                        // Visit method parameters
                        for (int i = 0; i < arguments.getExpressions().size() - 1; i++) {
                            arguments.getExpressions().set(i, replaceExpr(arguments.getExpression(i)));
                        }
                        // Transform closure
                        ClosureExpression closureExpression = (ClosureExpression) lastArg;
                        visitRuleClosure(closureExpression, call, displayName(call));
                        Expression replaced = new StaticMethodCallExpression(RULE_FACTORY, "decorate", new ArgumentListExpression(new VariableExpression(RULE_FACTORY_FIELD_NAME), closureExpression));
                        arguments.getExpressions().set(arguments.getExpressions().size() - 1, replaced);
                        return;
                    }
                }
            }
        }
        super.visitExpressionStatement(stat);
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        String methodName = call.getMethodAsString();
        if (call.isImplicitThis() && methodName != null && methodName.equals(DOLLAR)) {
            visitInputMethod(call);
            return;
        }
        // visit the method call, because one of the args may be an input method call
        super.visitMethodCallExpression(call);
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

            inputs.ownReference(modelPath, call.getLineNumber());
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

    public static String displayName(MethodCallExpression expression) {
        StringBuilder builder = new StringBuilder();
        if (!expression.isImplicitThis()) {
            builder.append(expression.getObjectExpression().getText());
            builder.append('.');
        }
        builder.append(expression.getMethodAsString());
        if (expression.getArguments() instanceof ArgumentListExpression) {
            ArgumentListExpression arguments = (ArgumentListExpression) expression.getArguments();
            boolean hasTrailingClosure = !arguments.getExpressions().isEmpty() && arguments.getExpression(arguments.getExpressions().size() - 1) instanceof ClosureExpression;
            List<Expression> otherArgs = hasTrailingClosure ? arguments.getExpressions().subList(0, arguments.getExpressions().size() - 1) : arguments.getExpressions();
            if (!otherArgs.isEmpty() || !hasTrailingClosure) {
                builder.append("(");
                builder.append(Joiner.on(", ").join(CollectionUtils.collect(otherArgs, new Transformer<Object, Expression>() {
                    @Override
                    public Object transform(Expression expression) {
                        return expression.getText();
                    }
                })));
                builder.append(")");
            }
            if (hasTrailingClosure) {
                builder.append(" { ... }");
            }
        } else {
            builder.append("()");
        }
        return builder.toString();
    }
}
