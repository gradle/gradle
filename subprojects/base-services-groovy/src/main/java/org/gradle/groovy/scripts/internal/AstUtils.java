/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import com.google.common.base.Predicate;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.EmptyExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCall;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.internal.Pair;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
 * Self contained utility functions for dealing with AST.
 */
public abstract class AstUtils {

    private AstUtils() {
    }

    public static boolean isMethodOnThis(MethodCallExpression call, String name) {
        boolean hasName = call.getMethod() instanceof ConstantExpression && call.getMethod().getText().equals(name);
        return hasName && targetIsThis(call);
    }

    public static boolean targetIsThis(MethodCallExpression call) {
        Expression target = call.getObjectExpression();
        return target instanceof VariableExpression && target.getText().equals("this");
    }

    public static void visitScriptCode(SourceUnit source, GroovyCodeVisitor transformer) {
        source.getAST().getStatementBlock().visit(transformer);
        for (Object method : source.getAST().getMethods()) {
            MethodNode methodNode = (MethodNode) method;
            methodNode.getCode().visit(transformer);
        }
    }

    public static ClassNode getScriptClass(SourceUnit source) {
        if (source.getAST().getStatementBlock().getStatements().isEmpty() && source.getAST().getMethods().isEmpty()) {
            // There is no script class when there are no statements or methods declared in the script
            return null;
        }
        return source.getAST().getClasses().get(0);
    }

    public static void removeMethod(ClassNode declaringClass, MethodNode methodNode) {
        declaringClass.getMethods().remove(methodNode);
        declaringClass.getDeclaredMethods(methodNode.getName()).clear();
    }

    public static void filterAndTransformStatements(SourceUnit source, StatementTransformer transformer) {
        ListIterator<Statement> statementIterator = source.getAST().getStatementBlock().getStatements().listIterator();
        while (statementIterator.hasNext()) {
            Statement originalStatement = statementIterator.next();
            Statement transformedStatement = transformer.transform(source, originalStatement);
            if (transformedStatement == null) {
                statementIterator.remove();
            } else if (transformedStatement != originalStatement) {
                statementIterator.set(transformedStatement);
            }
        }
    }

    public static boolean isVisible(SourceUnit source, String className) {
        try {
            source.getClassLoader().loadClass(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Nullable
    public static MethodCallExpression extractBareMethodCall(Statement statement) {
        if (!(statement instanceof ExpressionStatement)) {
            return null;
        }

        ExpressionStatement expressionStatement = (ExpressionStatement) statement;
        if (!(expressionStatement.getExpression() instanceof MethodCallExpression)) {
            return null;
        }

        MethodCallExpression methodCall = (MethodCallExpression) expressionStatement.getExpression();
        if (!targetIsThis(methodCall)) {
            return null;
        }

        return methodCall;
    }

    @Nullable
    public static String extractConstantMethodName(MethodCallExpression methodCall) {
        if (!(methodCall.getMethod() instanceof ConstantExpression)) {
            return null;
        }

        return methodCall.getMethod().getText();
    }

    @Nullable
    public static ScriptBlock detectScriptBlock(Statement statement) {
        MethodCallExpression methodCall = extractBareMethodCall(statement);
        if (methodCall == null) {
            return null;
        }

        String methodName = extractConstantMethodName(methodCall);
        if (methodName == null) {
            return null;
        }

        ClosureExpression closureExpression = getSingleClosureArg(methodCall);
        return closureExpression == null ? null : new ScriptBlock(methodName, methodCall, closureExpression);
    }

    public static Pair<ClassExpression, ClosureExpression> getClassAndClosureArgs(MethodCall methodCall) {
        if (!(methodCall.getArguments() instanceof ArgumentListExpression)) {
            return null;
        }

        ArgumentListExpression args = (ArgumentListExpression) methodCall.getArguments();
        if (args.getExpressions().size() == 2 && args.getExpression(0) instanceof ClassExpression && args.getExpression(1) instanceof ClosureExpression) {
            return Pair.of((ClassExpression) args.getExpression(0), (ClosureExpression) args.getExpression(1));
        } else {
            return null;
        }
    }

    public static ClassExpression getClassArg(MethodCall methodCall) {
        if (!(methodCall.getArguments() instanceof ArgumentListExpression)) {
            return null;
        }

        ArgumentListExpression args = (ArgumentListExpression) methodCall.getArguments();
        if (args.getExpressions().size() == 1 && args.getExpression(0) instanceof ClassExpression) {
            return (ClassExpression) args.getExpression(0);
        } else {
            return null;
        }
    }

    public static ClosureExpression getSingleClosureArg(MethodCall methodCall) {
        if (!(methodCall.getArguments() instanceof ArgumentListExpression)) {
            return null;
        }

        ArgumentListExpression args = (ArgumentListExpression) methodCall.getArguments();
        if (args.getExpressions().size() == 1 && args.getExpression(0) instanceof ClosureExpression) {
            return (ClosureExpression) args.getExpression(0);
        } else {
            return null;
        }
    }

    @Nullable
    public static ScriptBlock detectScriptBlock(Statement statement, Predicate<? super ScriptBlock> predicate) {
        ScriptBlock scriptBlock = detectScriptBlock(statement);
        if (scriptBlock != null && predicate.apply(scriptBlock)) {
            return scriptBlock;
        } else {
            return null;
        }
    }

    @Nullable
    public static ScriptBlock detectScriptBlock(Statement statement, final Collection<String> names) {
        return detectScriptBlock(statement, new Predicate<ScriptBlock>() {
            @Override
            public boolean apply(ScriptBlock input) {
                return names.contains(input.getName());
            }
        });
    }

    public static boolean isOfType(ConstantExpression constantExpression, Class<?> type) {
        return constantExpression.getType().getName().equals(type.getName());
    }

    @Nullable
    public static ConstantExpression hasSingleConstantStringArg(MethodCallExpression call) {
        return hasSingleConstantArgOfType(call, String.class);
    }

    @Nullable
    public static ConstantExpression hasSingleConstantArgOfType(MethodCallExpression call, Class<?> type) {
        Expression arguments = call.getArguments();
        if (arguments instanceof ArgumentListExpression) {
            ArgumentListExpression argumentList = (ArgumentListExpression) arguments;
            if (argumentList.getExpressions().size() == 1) {
                Expression argumentExpression = argumentList.getExpressions().get(0);
                if (argumentExpression instanceof ConstantExpression) {
                    ConstantExpression constantArgumentExpression = (ConstantExpression) argumentExpression;
                    if (isOfType(constantArgumentExpression, type)) {
                        return constantArgumentExpression;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    public static PropertyExpression hasSinglePropertyExpressionArgument(MethodCallExpression call) {
        ArgumentListExpression argumentList = (ArgumentListExpression) call.getArguments();
        if (argumentList.getExpressions().size() == 1) {
            Expression argumentExpression = argumentList.getExpressions().get(0);
            if (argumentExpression instanceof PropertyExpression) {
                return (PropertyExpression) argumentExpression;
            }
        }

        return null;
    }

    public static Iterable<? extends Statement> unpack(Statement statement) {
        if (statement instanceof BlockStatement) {
            return ((BlockStatement) statement).getStatements();
        } else {
            return Collections.singleton(statement);
        }
    }

    public static MethodNode getGeneratedClosureImplMethod(ClassNode classNode) {
        if (!classNode.implementsInterface(ClassHelper.GENERATED_CLOSURE_Type)) {
            throw new IllegalArgumentException("expecting generated closure class node");
        }

        List<MethodNode> doCallMethods = classNode.getDeclaredMethods("doCall");
        return doCallMethods.get(0);
    }

    /**
     * Returns true if the given statement may have some effect as part of a script body.
     * Returns false when the given statement may be ignored, provided all other statements in the script body may also be ignored.
     */
    public static boolean mayHaveAnEffect(Statement statement) {
        if (statement instanceof ReturnStatement) {
            ReturnStatement returnStatement = (ReturnStatement) statement;
            if (returnStatement.getExpression() instanceof ConstantExpression) {
                return false;
            }
        } else if (statement instanceof ExpressionStatement) {
            ExpressionStatement expressionStatement = (ExpressionStatement) statement;
            if (expressionStatement.getExpression() instanceof ConstantExpression) {
                return false;
            }
            if (expressionStatement.getExpression() instanceof DeclarationExpression) {
                DeclarationExpression declarationExpression = (DeclarationExpression) expressionStatement.getExpression();
                if (declarationExpression.getRightExpression() instanceof EmptyExpression || declarationExpression.getRightExpression() instanceof ConstantExpression) {
                    return false;
                }
            }
        }
        return true;
    }
}
