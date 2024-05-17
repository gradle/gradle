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

package org.gradle.groovy.scripts.internal;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.classgen.BytecodeExpression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;

public class RestrictiveCodeVisitor extends CodeVisitorSupport {

    private final SourceUnit sourceUnit;
    private final String message;

    public RestrictiveCodeVisitor(SourceUnit sourceUnit, String message) {
        this.sourceUnit = sourceUnit;
        this.message = message;
    }

    protected void restrict(ASTNode astNode) {
        restrict(astNode, message);
    }

    protected void restrict(ASTNode astNode, String message) {
        sourceUnit.getErrorCollector().addError(
                new SyntaxException(message, astNode.getLineNumber(), astNode.getColumnNumber()),
                sourceUnit
        );
    }

    @Override
    public void visitBlockStatement(BlockStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitForLoop(ForStatement forLoop) {
        restrict(forLoop);
    }

    @Override
    public void visitWhileLoop(WhileStatement loop) {
        restrict(loop);
    }

    @Override
    public void visitDoWhileLoop(DoWhileStatement loop) {
        restrict(loop);
    }

    @Override
    public void visitIfElse(IfStatement ifElse) {
        restrict(ifElse);
    }

    @Override
    public void visitExpressionStatement(ExpressionStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitReturnStatement(ReturnStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitAssertStatement(AssertStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitTryCatchFinally(TryCatchStatement finally1) {
        restrict(finally1);
    }

    @Override
    public void visitSwitch(SwitchStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitCaseStatement(CaseStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitBreakStatement(BreakStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitContinueStatement(ContinueStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitThrowStatement(ThrowStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitSynchronizedStatement(SynchronizedStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitCatchStatement(CatchStatement statement) {
        restrict(statement);
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        restrict(call);
    }

    @Override
    public void visitStaticMethodCallExpression(StaticMethodCallExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitConstructorCallExpression(ConstructorCallExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitTernaryExpression(TernaryExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitShortTernaryExpression(ElvisOperatorExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitPrefixExpression(PrefixExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitPostfixExpression(PostfixExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitBooleanExpression(BooleanExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitTupleExpression(TupleExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitMapExpression(MapExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitMapEntryExpression(MapEntryExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitListExpression(ListExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitRangeExpression(RangeExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitPropertyExpression(PropertyExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitAttributeExpression(AttributeExpression attributeExpression) {
        restrict(attributeExpression);
    }

    @Override
    public void visitFieldExpression(FieldExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitMethodPointerExpression(MethodPointerExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitConstantExpression(ConstantExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitClassExpression(ClassExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitVariableExpression(VariableExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitGStringExpression(GStringExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitArrayExpression(ArrayExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitSpreadExpression(SpreadExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitSpreadMapExpression(SpreadMapExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitNotExpression(NotExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitUnaryMinusExpression(UnaryMinusExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitUnaryPlusExpression(UnaryPlusExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitCastExpression(CastExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitArgumentlistExpression(ArgumentListExpression expression) {
        restrict(expression);
    }

    @Override
    public void visitClosureListExpression(ClosureListExpression closureListExpression) {
        restrict(closureListExpression);
    }

    @Override
    public void visitBytecodeExpression(BytecodeExpression expression) {
        restrict(expression);
    }
}
