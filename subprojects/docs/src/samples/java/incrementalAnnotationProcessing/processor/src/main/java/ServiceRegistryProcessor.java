import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Set;

@SupportedAnnotationTypes("Service")
public class ServiceRegistryProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    // tag::options-of-dynamic-processor[]
    @Override
    public Set<String> getSupportedOptions() {
        return Collections.singleton("org.gradle.annotation.processing.aggregating");
    }
    // end::options-of-dynamic-processor[]

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        TypeElement serviceAnnotation = processingEnv.getElementUtils().getTypeElement("Service");
        if (!annotations.equals(Collections.singleton(serviceAnnotation))) {
            return false;
        }
        try {
            createServiceRegistry(serviceAnnotation, roundEnv);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not create ServiceRegistry: " + e);
        }
        return true;
    }

    private void createServiceRegistry(TypeElement serviceAnnotation, RoundEnvironment roundEnv) throws IOException {
        Filer filer = processingEnv.getFiler();
        // tag::aggregating-annotation-processor[]
        JavaFileObject serviceRegistry = filer.createSourceFile("ServiceRegistry");
        Writer writer = serviceRegistry.openWriter();
        writer.write("public class ServiceRegistry {");
        for (Element service : roundEnv.getElementsAnnotatedWith(serviceAnnotation)) {
            addServiceCreationMethod(writer, (TypeElement) service);
        }
        writer.write("}");
        writer.close();
        // end::aggregating-annotation-processor[]
    }

    private void addServiceCreationMethod(Writer writer, TypeElement service) throws IOException {
        Name qualifiedName = service.getQualifiedName();
        Name simpleName = service.getSimpleName();
        writer.write("  public " + qualifiedName + " create" + simpleName + "() {");
        writer.write("      return new " + service.getQualifiedName() + "();");
        writer.write("  }");
    }

}
