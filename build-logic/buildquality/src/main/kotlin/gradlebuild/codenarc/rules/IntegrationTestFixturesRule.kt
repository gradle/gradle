package gradlebuild.codenarc.rules

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codenarc.rule.AbstractAstVisitor
import org.codenarc.rule.AbstractAstVisitorRule
import org.codenarc.util.AstUtil


class IntegrationTestFixturesRule : AbstractAstVisitorRule() {
    override fun getName(): String = "IntegrationTestFixtures"

    override fun getPriority(): Int = 1

    override fun setPriority(priority: Int) {
        throw UnsupportedOperationException()
    }

    override fun setName(name: String?) {
        throw UnsupportedOperationException()
    }

    override fun getDescription(): String = "Reports incorrect usages of integration test fixtures"

    override fun getAstVisitorClass(): Class<*> = IntegrationTestFixtureVisitor::class.java
}


class IntegrationTestFixtureVisitor : AbstractAstVisitor() {

    /**
     * Determines if the class is an integration test. It's a bit weak, but we cannot
     * rely on the class node analysis since it doesn't give the right superclass because
     * resolution is not complete when this visitor is executed.
     */
    private
    fun isIntegrationTest(current: ClassNode) = current.name.endsWith("Test")
        || current.name.endsWith("Spec")

    override fun shouldVisitMethod(node: MethodNode): Boolean = isIntegrationTest(node.declaringClass)

    override fun visitMethodCallExpression(mce: MethodCallExpression) {
        if (AstUtil.isMethodNamed(mce, "contains")) {
            checkOutputContains(mce)
        } else if (AstUtil.isMethodNamed(mce, "assertOutputContains")) {
            val objectExpr = mce.objectExpression
            checkIndirectOutputContains(objectExpr, mce)
        }
    }

    private
    fun checkOutputContains(call: MethodCallExpression) {
        val receiver = call.receiver!!
        if (receiver is PropertyExpression) {
            if (receiver.propertyAsString == "output") {
                val objectExpr = receiver.objectExpression!!
                checkIndirectOutputContains(objectExpr, call)
            }
        }
    }

    private
    fun checkIndirectOutputContains(objectExpr: Expression, call: MethodCallExpression) {
        if (objectExpr is VariableExpression
            && objectExpr.name == "result"
        ) {
            val arg = AstUtil.getNodeText(call.arguments, sourceCode)
            addViolation(call, "Should use outputContains($arg) or failure.assertHasCause($arg) instead")
        }
    }
}
