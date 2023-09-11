import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

@GroovyASTTransformation
public class MagicInterfaceTransform implements ASTTransformation {
    public void visit(ASTNode[] nodes, SourceUnit source) {
        ClassNode clazz = (ClassNode) nodes[1];
        clazz.addInterface(new ClassNode(Marker.class));
    }
}
