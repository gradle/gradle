import org.codehaus.groovy.transform.GroovyASTTransformationClass;

@GroovyASTTransformationClass(classes = {MagicFieldTransform.class})
public @interface MagicField {}
