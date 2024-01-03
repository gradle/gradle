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

package org.gradle.internal.instrumentation.processor.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.RequestExtra.OriginatingElement;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGenerator.GenerationResult.CanGenerateClasses;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGenerator.GenerationResult.CodeFailures;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator.GenerationResult.CanGenerateResource;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator.GenerationResult.ResourceFailures;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class InstrumentationCodeGeneratorHost {
    private final Filer filer;
    private final Messager messager;
    private final InstrumentationCodeGenerator codeGenerator;
    private final List<InstrumentationResourceGenerator> resourceGenerators;

    public InstrumentationCodeGeneratorHost(
        Filer filer,
        Messager messager,
        InstrumentationCodeGenerator codeGenerator,
        List<InstrumentationResourceGenerator> resourceGenerators
    ) {
        this.filer = filer;
        this.messager = messager;
        this.codeGenerator = codeGenerator;
        this.resourceGenerators = resourceGenerators;
    }

    public void generateCodeForRequestedInterceptors(
        Collection<CallInterceptionRequest> interceptionRequests
    ) {
        InstrumentationCodeGenerator.GenerationResult result = codeGenerator.generateCodeForRequestedInterceptors(interceptionRequests);
        if (result instanceof CanGenerateClasses) {
            for (String canonicalClassName : ((CanGenerateClasses) result).getClassNames()) {
                ClassName className = ClassName.bestGuess(canonicalClassName);
                TypeSpec.Builder builder = TypeSpec.classBuilder(className);
                CanGenerateClasses generateType = (CanGenerateClasses) result;
                getOriginatingElements(generateType.getCoveredRequests()).forEach(builder::addOriginatingElement);
                generateType.buildType(canonicalClassName, builder);
                TypeSpec generatedType = builder.build();
                JavaFile javaFile = JavaFile.builder(className.packageName(), generatedType).indent("    ").build();
                try {
                    javaFile.writeTo(filer);
                } catch (IOException e) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Failed to write generated source file in package " + className.packageName() + ", named " + generatedType.name);
                }
            }
        } else if (result instanceof CodeFailures) {
            printFailures((CodeFailures) result);
        }

        // Right now every InstrumentationResourceGenerator have to generate it's own resource, but if needed,
        // we can extend this and we can support write to composite resources in a similar way as we do for classes
        for (InstrumentationResourceGenerator resourceGenerator : resourceGenerators) {
            generateResource(resourceGenerator, interceptionRequests);
        }
    }

    private void generateResource(InstrumentationResourceGenerator resourceGenerator, Collection<CallInterceptionRequest> interceptionRequests) {
        Collection<CallInterceptionRequest> filteredRequests = resourceGenerator.filterRequestsForResource(interceptionRequests);
        if (filteredRequests.isEmpty()) {
            return;
        }

        InstrumentationResourceGenerator.GenerationResult result = resourceGenerator.generateResourceForRequests(filteredRequests);
        if (result instanceof CanGenerateResource) {
            CanGenerateResource resourceResult = (CanGenerateResource) result;
            try {
                Element[] originatingElements = getOriginatingElements(filteredRequests).toArray(new Element[0]);
                FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, resourceResult.getPackageName(), resourceResult.getName(), originatingElements);
                try (OutputStream outputStream = resource.openOutputStream()) {
                    ((CanGenerateResource) result).write(outputStream);
                }
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to write generated resource file in package " + resourceResult.getPackageName() + ", named " + resourceResult.getName() + ": " + e.getMessage());
            }
        } else if (result instanceof ResourceFailures) {
            printFailures((ResourceFailures) result);
        }
    }

    private void printFailures(HasFailures failure) {
        failure.getFailureDetails().forEach(details -> {
            Optional<ExecutableElement> maybeOriginatingElement =
                    Optional.ofNullable(details.request)
                            .flatMap(presentRequest -> presentRequest.getRequestExtras().getByType(OriginatingElement.class).map(OriginatingElement::getElement));
            if (maybeOriginatingElement.isPresent()) {
                messager.printMessage(Diagnostic.Kind.ERROR, details.reason, maybeOriginatingElement.get());
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, details.reason);
            }
        });
    }

    private static Set<ExecutableElement> getOriginatingElements(Collection<CallInterceptionRequest> coveredRequests) {
        return coveredRequests.stream().map(requests ->
            requests.getRequestExtras().getByType(OriginatingElement.class).map(OriginatingElement::getElement).orElse(null)
        ).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
