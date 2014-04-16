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

    public void visitBlockStatement(BlockStatement statement) {
        restrict(statement);
    }

    public void visitForLoop(ForStatement forLoop) {
        restrict(forLoop);
    }

    public void visitWhileLoop(WhileStatement loop) {
        restrict(loop);
    }

    public void visitDoWhileLoop(DoWhileStatement loop) {
        restrict(loop);
    }

    public void visitIfElse(IfStatement ifElse) {
        restrict(ifElse);
    }

    public void visitExpressionStatement(ExpressionStatement statement) {
        restrict(statement);
    }

    public void visitReturnStatement(ReturnStatement statement) {
        restrict(statement);
    }

    public void visitAssertStatement(AssertStatement statement) {
        restrict(statement);
    }

    public void visitTryCatchFinally(TryCatchStatement finally1) {
        restrict(finally1);
    }

    public void visitSwitch(SwitchStatement statement) {
        restrict(statement);
    }

    public void visitCaseStatement(CaseStatement statement) {
        restrict(statement);
    }

    public void visitBreakStatement(BreakStatement statement) {
        restrict(statement);
    }

    public void visitContinueStatement(ContinueStatement statement) {
        restrict(statement);
    }

    public void visitThrowStatement(ThrowStatement statement) {
        restrict(statement);
    }

    public void visitSynchronizedStatement(SynchronizedStatement statement) {
        restrict(statement);
    }

    public void visitCatchStatement(CatchStatement statement) {
        restrict(statement);
    }

    public void visitMethodCallExpression(MethodCallExpression call) {
        restrict(call);
    }

    public void visitStaticMethodCallExpression(StaticMethodCallExpression expression) {
        restrict(expression);
    }

    public void visitConstructorCallExpression(ConstructorCallExpression expression) {
        restrict(expression);
    }

    public void visitTernaryExpression(TernaryExpression expression) {
        restrict(expression);
    }

    public void visitShortTernaryExpression(ElvisOperatorExpression expression) {
        restrict(expression);
    }

    public void visitBinaryExpression(BinaryExpression expression) {
        restrict(expression);
    }

    public void visitPrefixExpression(PrefixExpression expression) {
        restrict(expression);
    }

    public void visitPostfixExpression(PostfixExpression expression) {
        restrict(expression);
    }

    public void visitBooleanExpression(BooleanExpression expression) {
        restrict(expression);
    }

    public void visitClosureExpression(ClosureExpression expression) {
        restrict(expression);
    }

    public void visitTupleExpression(TupleExpression expression) {
        restrict(expression);
    }

    public void visitMapExpression(MapExpression expression) {
        restrict(expression);
    }

    public void visitMapEntryExpression(MapEntryExpression expression) {
        restrict(expression);
    }

    public void visitListExpression(ListExpression expression) {
        restrict(expression);
    }

    public void visitRangeExpression(RangeExpression expression) {
        restrict(expression);
    }

    public void visitPropertyExpression(PropertyExpression expression) {
        restrict(expression);
    }

    public void visitAttributeExpression(AttributeExpression attributeExpression) {
        restrict(attributeExpression);
    }

    public void visitFieldExpression(FieldExpression expression) {
        restrict(expression);
    }

    public void visitMethodPointerExpression(MethodPointerExpression expression) {
        restrict(expression);
    }

    public void visitConstantExpression(ConstantExpression expression) {
        restrict(expression);
    }

    public void visitClassExpression(ClassExpression expression) {
        restrict(expression);
    }

    public void visitVariableExpression(VariableExpression expression) {
        restrict(expression);
    }

    public void visitDeclarationExpression(DeclarationExpression expression) {
        restrict(expression);
    }

    public void visitGStringExpression(GStringExpression expression) {
        restrict(expression);
    }

    public void visitArrayExpression(ArrayExpression expression) {
        restrict(expression);
    }

    public void visitSpreadExpression(SpreadExpression expression) {
        restrict(expression);
    }

    public void visitSpreadMapExpression(SpreadMapExpression expression) {
        restrict(expression);
    }

    public void visitNotExpression(NotExpression expression) {
        restrict(expression);
    }

    public void visitUnaryMinusExpression(UnaryMinusExpression expression) {
        restrict(expression);
    }

    public void visitUnaryPlusExpression(UnaryPlusExpression expression) {
        restrict(expression);
    }

    public void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
        restrict(expression);
    }

    public void visitCastExpression(CastExpression expression) {
        restrict(expression);
    }

    public void visitArgumentlistExpression(ArgumentListExpression expression) {
        restrict(expression);
    }

    public void visitClosureListExpression(ClosureListExpression closureListExpression) {
        restrict(closureListExpression);
    }

    public void visitBytecodeExpression(BytecodeExpression expression) {
        restrict(expression);
    }
}
