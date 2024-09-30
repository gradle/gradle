/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.instrumentation.processor;

import org.gradle.internal.Cast;
import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.processor.codegen.CompositeInstrumentationCodeGenerator;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGeneratorHost;
import org.gradle.internal.instrumentation.processor.extensibility.AnnotatedMethodReaderExtension;
import org.gradle.internal.instrumentation.processor.extensibility.ClassLevelAnnotationsContributor;
import org.gradle.internal.instrumentation.processor.extensibility.CodeGeneratorContributor;
import org.gradle.internal.instrumentation.processor.extensibility.InstrumentationProcessorExtension;
import org.gradle.internal.instrumentation.processor.extensibility.RequestPostProcessorExtension;
import org.gradle.internal.instrumentation.processor.extensibility.ResourceGeneratorContributor;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.ReadRequestContext;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils;
import org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils;

import javax.annotation.Nonnull;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.getExecutableElementsFromElements;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public abstract class AbstractInstrumentationProcessor extends AbstractProcessor {

    public static final String PROJECT_NAME_OPTIONS = "org.gradle.annotation.processing.instrumented.project";

    protected abstract Collection<InstrumentationProcessorExtension> getExtensions();

    @Override
    public Set<String> getSupportedOptions() {
        return new HashSet<>(Arrays.asList("org.gradle.annotation.processing.aggregating", PROJECT_NAME_OPTIONS));
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return getSupportedAnnotations().stream().map(Class::getName).collect(Collectors.toSet());
    }

    private Set<Class<? extends Annotation>> getSupportedAnnotations() {
        return getExtensionsByType(ClassLevelAnnotationsContributor.class).stream()
            .flatMap(it -> it.contributeClassLevelAnnotationTypes().stream())
            .collect(Collectors.toSet());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // GetAnnotatedElementsSkippingPackageRoots() is the same as roundEnv.getElementsAnnotatedWith() but skips package roots.
        // See issue: https://github.com/gradle/gradle/issues/29926
        Stream<? extends Element> annotatedTypes = getAnnotatedElementsSkippingPackageRoots(roundEnv, getSupportedAnnotations())
            .flatMap(element -> findActualTypesToVisit(element).stream())
            .sorted(Comparator.comparing(TypeUtils::elementQualifiedName));
        collectAndProcessRequests(annotatedTypes);
        return false;
    }

    private Set<Element> findActualTypesToVisit(Element typeElement) {
        Optional<? extends AnnotationMirror> annotationMirror = AnnotationUtils.findAnnotationMirror(typeElement, VisitForInstrumentation.class);
        if (!annotationMirror.isPresent()) {
            return Collections.singleton(typeElement);
        }

        @SuppressWarnings("unchecked")
        List<AnnotationValue> values = (List<AnnotationValue>) AnnotationUtils.findAnnotationValue(annotationMirror.get(), "value")
            .orElseThrow(() -> new IllegalStateException("missing annotation value"))
            .getValue();
        return values.stream()
            .map(v -> processingEnv.getTypeUtils().asElement((TypeMirror) v.getValue()))
            .collect(Collectors.toSet());
    }

    private <T extends InstrumentationProcessorExtension> Collection<T> getExtensionsByType(Class<T> type) {
        return Cast.uncheckedCast(getExtensions().stream().filter(type::isInstance).collect(Collectors.toList()));
    }

    private void collectAndProcessRequests(Stream<? extends Element> annotatedElements) {
        Collection<AnnotatedMethodReaderExtension> readers = getExtensionsByType(AnnotatedMethodReaderExtension.class);

        List<ExecutableElement> allMethodElementsInAnnotatedClasses = getExecutableElementsFromElements(annotatedElements);

        Map<ExecutableElement, List<CallInterceptionRequestReader.Result.InvalidRequest>> errors = new LinkedHashMap<>();
        List<CallInterceptionRequestReader.Result.Success> successResults = new ArrayList<>();
        readRequests(readers, allMethodElementsInAnnotatedClasses, errors, successResults);

        if (!errors.isEmpty()) {
            Messager messager = processingEnv.getMessager();
            errors.forEach((element, elementErrors) -> elementErrors.forEach(error -> messager.printMessage(Diagnostic.Kind.ERROR, error.reason, element)));
            return;
        }

        List<CallInterceptionRequest> requests = postProcessRequests(successResults);

        runCodeGeneration(requests);
    }

    private static void readRequests(Collection<AnnotatedMethodReaderExtension> readers, List<ExecutableElement> allMethodElementsInAnnotatedClasses, Map<ExecutableElement, List<CallInterceptionRequestReader.Result.InvalidRequest>> errors, List<CallInterceptionRequestReader.Result.Success> successResults) {
        ReadRequestContext context = new ReadRequestContext();
        for (ExecutableElement methodElement : allMethodElementsInAnnotatedClasses) {
            for (AnnotatedMethodReaderExtension reader : readers) {
                Collection<CallInterceptionRequestReader.Result> readerResults = reader.readRequest(methodElement, context);
                for (CallInterceptionRequestReader.Result readerResult : readerResults) {
                    if (readerResult instanceof CallInterceptionRequestReader.Result.InvalidRequest) {
                        errors.computeIfAbsent(methodElement, key -> new ArrayList<>()).add((CallInterceptionRequestReader.Result.InvalidRequest) readerResult);
                    } else {
                        successResults.add((CallInterceptionRequestReader.Result.Success) readerResult);
                    }
                }
            }
        }
    }

    @Nonnull
    private List<CallInterceptionRequest> postProcessRequests(List<CallInterceptionRequestReader.Result.Success> successResults) {
        List<CallInterceptionRequest> requests = successResults.stream().map(CallInterceptionRequestReader.Result.Success::getRequest).collect(Collectors.toList());
        for (RequestPostProcessorExtension postProcessor : getExtensionsByType(RequestPostProcessorExtension.class)) {
            requests = requests.stream().flatMap(request -> postProcessor.postProcessRequest(request).stream()).collect(Collectors.toList());
        }
        return requests;
    }

    private void runCodeGeneration(List<CallInterceptionRequest> requests) {
        InstrumentationCodeGeneratorHost generatorHost = new InstrumentationCodeGeneratorHost(processingEnv.getFiler(),
            processingEnv.getMessager(),
            new CompositeInstrumentationCodeGenerator(
                getExtensionsByType(CodeGeneratorContributor.class).stream().map(CodeGeneratorContributor::contributeCodeGenerator).collect(Collectors.toList())
            ),
            getExtensionsByType(ResourceGeneratorContributor.class).stream().map(ResourceGeneratorContributor::contributeResourceGenerator).collect(Collectors.toList())
        );

        generatorHost.generateCodeForRequestedInterceptors(requests);
    }

    /**
     * Discover all elements annotated with the given annotations, skipping package roots.
     * This is similar to {@link RoundEnvironment#getElementsAnnotatedWith(Class)}, but skips package roots.
     * Since if package-info.java exist, we could discover types from other projects in the same package.
     *
     * See issue: https://github.com/gradle/gradle/issues/29926
     */
    private Stream<? extends Element> getAnnotatedElementsSkippingPackageRoots(
        RoundEnvironment roundEnvironment,
        Set<Class<? extends Annotation>> annotations
    ) {
        Set<TypeElement> annotationsAsElements = annotations.stream()
            .filter(annotation -> annotation.getCanonicalName() != null)
            .map(annotation -> processingEnv.getElementUtils().getTypeElement(annotation.getCanonicalName()))
            .collect(Collectors.toCollection(() -> new LinkedHashSet<>(annotations.size())));

        Set<Element> result = Collections.emptySet();
        AnnotationScanner scanner = new AnnotationScanner(processingEnv.getElementUtils());
        for (Element element : roundEnvironment.getRootElements()) {
            if (!(element instanceof PackageElement)) {
                result = scanner.scan(element, annotationsAsElements);
            }
        }
        return result.stream();
    }

    private static class AnnotationScanner extends ElementScanner8<Set<Element>, Set<TypeElement>> {
        private final Set<Element> annotatedElements = new LinkedHashSet<>();
        private final Elements elements;

        private AnnotationScanner(Elements elements) {
            super(Collections.emptySet());
            this.elements = elements;
        }

        @Override
        public Set<Element> scan(Element e, Set<TypeElement> annotations) {
            for (AnnotationMirror annotationMirror : elements.getAllAnnotationMirrors(e)) {
                if (annotations.contains((TypeElement) annotationMirror.getAnnotationType().asElement())) {
                    annotatedElements.add(e);
                    break;
                }
            }
            e.accept(this, annotations);
            return annotatedElements;
        }

        @Override
        public Set<Element> visitType(TypeElement e, Set<TypeElement> p) {
            // Type parameters are not considered to be enclosed by a type
            scan(e.getTypeParameters(), p);
            return super.visitType(e, p);
        }

        @Override
        public Set<Element> visitExecutable(ExecutableElement e, Set<TypeElement> p) {
            // Type parameters are not considered to be enclosed by an executable
            scan(e.getTypeParameters(), p);
            return super.visitExecutable(e, p);
        }
    }
}
