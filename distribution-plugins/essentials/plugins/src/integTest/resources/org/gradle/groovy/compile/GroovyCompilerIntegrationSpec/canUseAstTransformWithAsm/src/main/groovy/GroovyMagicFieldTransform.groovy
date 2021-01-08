import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.BytecodeExpression
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation
class GroovyMagicFieldTransform implements ASTTransformation, Opcodes {
    void visit(ASTNode[] nodes, SourceUnit source) {
        def clazz = (ClassNode) nodes[1]
        clazz.addField("magicField", ACC_PUBLIC, ClassHelper.STRING_TYPE, new StringExpressionThroughAsm())
    }

    private class StringExpressionThroughAsm extends BytecodeExpression {
        StringExpressionThroughAsm() {
            super(ClassHelper.STRING_TYPE)
        }

        @Override
        void visit(MethodVisitor methodVisitor) {
            methodVisitor.visitLdcInsn("magicValue")
        }
    }
}
