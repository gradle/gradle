import org.codehaus.groovy.transform.GroovyASTTransformationClass;

@GroovyASTTransformationClass(classes = {MagicInterfaceTransform.class})
public @interface MagicInterface {}
