import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import java.lang.reflect.Modifier

@GroovyASTTransformation
class GroovyMagicFieldTransform implements ASTTransformation {
    void visit(ASTNode[] nodes, SourceUnit source) {
        def clazz = (ClassNode) nodes[1]
        clazz.addField("magicField", Modifier.PUBLIC, ClassHelper.STRING_TYPE, new ConstantExpression("magicValue"))
    }
}
