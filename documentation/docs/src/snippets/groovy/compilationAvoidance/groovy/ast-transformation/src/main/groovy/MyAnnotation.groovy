import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@org.codehaus.groovy.transform.GroovyASTTransformationClass("MyASTTransformation")
public @interface MyAnnotation { }
