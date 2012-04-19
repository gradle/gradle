import org.codehaus.groovy.transform.GroovyASTTransformationClass

@GroovyASTTransformationClass(["GroovyMagicFieldTransform"])
public @interface GroovyMagicField {}
