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

import org.gradle.internal.instrumentation.api.annotations.CallInterceptors;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.processor.codegen.CompositeInstrumentationCodeGenerator;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGeneratorHost;
import org.gradle.internal.instrumentation.processor.codegen.groovy.InterceptGroovyCallsGenerator;
import org.gradle.internal.instrumentation.processor.codegen.InterceptJvmCallsGenerator;
import org.gradle.internal.instrumentation.processor.modelreader.AnnotationCallInterceptionRequestReader;
import org.gradle.internal.instrumentation.processor.modelreader.AnnotationCallInterceptionRequestReaderImpl;
import org.gradle.internal.instrumentation.processor.modelreader.CallInterceptionRequestReader;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class InstrumentationProcessor extends AbstractProcessor {

    private static final Class<CallInterceptors> ANNOTATION_CLASS = CallInterceptors.class;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Stream.of(ANNOTATION_CLASS.getName()).collect(Collectors.toSet());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedClasses = roundEnv.getElementsAnnotatedWith(ANNOTATION_CLASS);
        collectAndProcessRequests(annotatedClasses);
        return true;
    }

    private void collectAndProcessRequests(Collection<? extends Element> annotatedClassElements) {
        AnnotationCallInterceptionRequestReader reader = new AnnotationCallInterceptionRequestReaderImpl();

        List<ExecutableElement> allMethodElementsForGeneratedClass = annotatedClassElements.stream()
            .flatMap(typeElement -> typeElement.getEnclosedElements().stream())
            .filter(it -> it.getKind() == ElementKind.METHOD)
            .map(it -> (ExecutableElement) it)
            .collect(Collectors.toList());

        Map<ExecutableElement, CallInterceptionRequestReader.Result> modelReadResults = allMethodElementsForGeneratedClass.stream()
            .collect(Collectors.toMap(Function.identity(), reader::readRequest, (u, v) -> v, LinkedHashMap::new));

        Map<ExecutableElement, CallInterceptionRequestReader.Result.InvalidRequest> errors =
            modelReadResults.entrySet().stream().filter(it -> it.getValue() instanceof CallInterceptionRequestReader.Result.InvalidRequest)
                .collect(Collectors.toMap(Map.Entry::getKey, it -> (CallInterceptionRequestReader.Result.InvalidRequest) it.getValue(), (u, v) -> v, LinkedHashMap::new));

        if (!errors.isEmpty()) {
            Messager messager = processingEnv.getMessager();
            errors.forEach((element, error) -> messager.printMessage(Diagnostic.Kind.ERROR, error.reason, element));
        }

        List<CallInterceptionRequest> requests = modelReadResults.values().stream()
            .map(it -> (it instanceof CallInterceptionRequestReader.Result.Success) ? ((CallInterceptionRequestReader.Result.Success) it).getRequest() : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        InstrumentationCodeGeneratorHost generatorHost = new InstrumentationCodeGeneratorHost(processingEnv.getFiler(),
            processingEnv.getMessager(),

            // TODO: these should be provided by the extensions
            new CompositeInstrumentationCodeGenerator(
                Arrays.asList(
                    new InterceptGroovyCallsGenerator(),
                    new InterceptJvmCallsGenerator()
                )
            )
        );
        generatorHost.generateCodeForRequestedInterceptors(requests, annotatedClassElements);
    }
}
