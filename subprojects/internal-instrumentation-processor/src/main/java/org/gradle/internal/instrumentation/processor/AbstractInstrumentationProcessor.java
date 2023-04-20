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
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils;

import javax.annotation.Nonnull;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public abstract class AbstractInstrumentationProcessor extends AbstractProcessor {

    protected abstract Collection<InstrumentationProcessorExtension> getExtensions();

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.singleton("org.gradle.annotation.processing.aggregating");
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
        Stream<? extends Element> annotatedTypes = getSupportedAnnotations().stream()
            .flatMap(annotation -> roundEnv.getElementsAnnotatedWith(annotation).stream())
            .flatMap(element -> findActualTypesToVisit(element).stream())
            .sorted(Comparator.comparing(AbstractInstrumentationProcessor::elementQualifiedName));
        collectAndProcessRequests(annotatedTypes);
        return true;
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

        List<ExecutableElement> allMethodElementsInAnnotatedClasses = getExecutableElementsFromAnnotatedElements(annotatedElements);

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

    @Nonnull
    private static List<ExecutableElement> getExecutableElementsFromAnnotatedElements(Stream<? extends Element> annotatedClassElements) {
        return annotatedClassElements
            .flatMap(element -> element.getKind() == ElementKind.METHOD ? Stream.of(element) : element.getEnclosedElements().stream())
            .filter(it -> it.getKind() == ElementKind.METHOD)
            .map(it -> (ExecutableElement) it)
            // Ensure that the elements have a stable order, as the annotation processing engine does not guarantee that for type elements.
            // The order in which the executable elements are listed should be the order in which they appear in the code but
            // we take an extra measure of care here and ensure the ordering between all elements.
            .sorted(Comparator.comparing(AbstractInstrumentationProcessor::elementQualifiedName))
            .distinct()
            .collect(Collectors.toList());
    }

    private static void readRequests(Collection<AnnotatedMethodReaderExtension> readers, List<ExecutableElement> allMethodElementsInAnnotatedClasses, Map<ExecutableElement, List<CallInterceptionRequestReader.Result.InvalidRequest>> errors, List<CallInterceptionRequestReader.Result.Success> successResults) {
        for (ExecutableElement methodElement : allMethodElementsInAnnotatedClasses) {
            for (AnnotatedMethodReaderExtension reader : readers) {
                Collection<CallInterceptionRequestReader.Result> readerResults = reader.readRequest(methodElement);
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
            )
        );

        generatorHost.generateCodeForRequestedInterceptors(requests);
    }

    private static String elementQualifiedName(Element element) {
        if (element instanceof ExecutableElement) {
            String enclosingTypeName = ((TypeElement) element.getEnclosingElement()).getQualifiedName().toString();
            return enclosingTypeName + "." + element.getSimpleName();
        } else if (element instanceof TypeElement) {
            return ((TypeElement) element).getQualifiedName().toString();
        } else {
            throw new IllegalArgumentException("Unsupported element type to read qualified name from: " + element.getClass());
        }
    }
}
