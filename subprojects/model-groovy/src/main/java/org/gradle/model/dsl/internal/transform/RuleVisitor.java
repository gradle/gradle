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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
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
import org.gradle.internal.SystemProperties;
import org.gradle.model.dsl.internal.inputs.RuleInputAccess;
import org.gradle.model.dsl.internal.inputs.RuleInputAccessBacking;
import org.gradle.model.internal.core.ModelPath;

import java.util.List;
import java.util.Map;

// This is explicitly not threadsafe
public class RuleVisitor extends CodeVisitorSupport {

    public static final String INVALID_ARGUMENT_LIST = "argument list must be exactly 1 literal non empty string";

    private static final String AST_NODE_METADATA_INPUTS_KEY = RuleVisitor.class.getName() + ".inputs";
    public static final String AST_NODE_METADATA_LOCATION_KEY = RuleVisitor.class.getName() + ".location";

    private static final String DOLLAR = "$";
    private static final String INPUT = "input";
    private static final ClassNode ANNOTATION_CLASS_NODE = new ClassNode(RuleMetadata.class);
    private static final ClassNode CONTEXTUAL_INPUT_TYPE = new ClassNode(RuleInputAccessBacking.class);
    private static final ClassNode ACCESS_API_TYPE = new ClassNode(RuleInputAccess.class);
    private static final String GET_ACCESS = "getAccess";

    private static final String ACCESS_HOLDER_FIELD = "_" + RuleInputAccess.class.getName().replace(".", "_");

    private final SourceUnit sourceUnit;
    private ImmutableListMultimap.Builder<String, Integer> inputs;
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
            metadataAnnotation.addMember("lineNumber", new ConstantExpression(sourceLocation.getLineNumber()));
            metadataAnnotation.addMember("columnNumber", new ConstantExpression(sourceLocation.getColumnNumber()));

            ListMultimap<String, Integer> inputs = closureCode.getNodeMetaData(AST_NODE_METADATA_INPUTS_KEY);
            if (!inputs.isEmpty()) {
                List<Expression> pathValues = Lists.newArrayListWithCapacity(inputs.size());
                List<Expression> lineNumberValues = Lists.newArrayListWithCapacity(inputs.size());
                for (Map.Entry<String, List<Integer>> input : Multimaps.asMap(inputs).entrySet()) {
                    pathValues.add(new ConstantExpression(input.getKey()));
                    lineNumberValues.add(new ConstantExpression(input.getValue().get(0)));
                }

                metadataAnnotation.addMember("inputPaths", new ListExpression(pathValues));
                metadataAnnotation.addMember("inputLineNumbers", new ListExpression(lineNumberValues));
            }

            node.addAnnotation(metadataAnnotation);
        }
    }

    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        if (inputs == null) {
            inputs = ImmutableListMultimap.builder();
            try {
                accessVariable = new VariableExpression(ACCESS_HOLDER_FIELD, ACCESS_API_TYPE);

                super.visitClosureExpression(expression);

                BlockStatement code = (BlockStatement) expression.getCode();
                code.setNodeMetaData(AST_NODE_METADATA_INPUTS_KEY, inputs.build());
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
                String message = "Invalid model path given as rule input." + SystemProperties.getLineSeparator()
                        + "  > " + e.getMessage();
                if (e.getCause() != null) {
                    // if there is a cause, it's an invalid name exception
                    message += SystemProperties.getLineSeparator() + "    > " + e.getCause().getMessage();
                }
                error(argExpression, message);
                return;
            }

            inputs.put(modelPath, call.getLineNumber());
            call.setObjectExpression(new VariableExpression(accessVariable));
            call.setMethod(new ConstantExpression(INPUT));
        }
    }

    private void error(ASTNode call, String message) {
        SyntaxException syntaxException = new SyntaxException(message, call.getLineNumber(), call.getColumnNumber());
        sourceUnit.getErrorCollector().addError(syntaxException, sourceUnit);
    }
}
