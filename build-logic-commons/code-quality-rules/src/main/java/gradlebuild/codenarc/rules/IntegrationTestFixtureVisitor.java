/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.codenarc.rules;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codenarc.rule.AbstractAstVisitor;
import org.codenarc.util.AstUtil;

public class IntegrationTestFixtureVisitor extends AbstractAstVisitor {

    /**
     * Determines if the class is an integration test. It's a bit weak, but we cannot
     * rely on the class node analysis since it doesn't give the right superclass because
     * resolution is not complete when this visitor is executed.
     */
    private boolean isIntegrationTest(ClassNode current) {
        return current.getName().endsWith("Test") || current.getName().endsWith("Spec");
    }

    @Override
    protected boolean shouldVisitMethod(MethodNode node) {
        return isIntegrationTest(node.getDeclaringClass());
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression mce) {
        if (AstUtil.isMethodNamed(mce, "contains")) {
            checkOutputContains(mce);
        } else if (AstUtil.isMethodNamed(mce, "assertOutputContains")) {
            Expression objectExpr = mce.getObjectExpression();
            checkIndirectOutputContains(objectExpr, mce);
        }
    }

    private void checkOutputContains(MethodCallExpression call) {
        ASTNode receiver = call.getReceiver();
        if (receiver instanceof PropertyExpression) {
            if (((PropertyExpression) receiver).getPropertyAsString().equals("output")) {
                Expression objectExpr = ((PropertyExpression) receiver).getObjectExpression();
                checkIndirectOutputContains(objectExpr, call);
            }
        }
    }

    private void checkIndirectOutputContains(Expression objectExpr, MethodCallExpression call) {
        if (objectExpr instanceof VariableExpression && ((VariableExpression) objectExpr).getName().equals("result")) {
            String arg = AstUtil.getNodeText(call.getArguments(), getSourceCode());
            addViolation(call, "Should use outputContains(" + arg + ") or failure.assertHasCause(" + arg + ") instead");
        }
    }
}
