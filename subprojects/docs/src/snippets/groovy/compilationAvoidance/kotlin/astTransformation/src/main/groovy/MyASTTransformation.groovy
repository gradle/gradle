import org.codehaus.groovy.transform.*;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.control.*;
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class MyASTTransformation extends AbstractASTTransformation {
    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        println "AST transformation!"
    }
}
