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

import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.model.RequestExtra.OriginatingElement;
import org.gradle.internal.instrumentation.processor.extensibility.RequestPostProcessorExtension;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.function.Function;

public class AddGeneratedClassNameFlagFromClassLevelAnnotation implements RequestPostProcessorExtension {
    public AddGeneratedClassNameFlagFromClassLevelAnnotation(Class<? extends Annotation> interceptCallsAnnotation, Class<? extends Annotation> generatedClassNameProvidingAnnotation, Function<String, RequestExtra> produceFlagForGeneratedClassName) {
        this.interceptCallsAnnotation = interceptCallsAnnotation;
        this.generatedClassNameProvidingAnnotation = generatedClassNameProvidingAnnotation;
        this.produceFlagForGeneratedClassName = produceFlagForGeneratedClassName;
    }

    private final Class<? extends Annotation> interceptCallsAnnotation;
    private final Class<? extends Annotation> generatedClassNameProvidingAnnotation;
    private final Function<String, RequestExtra> produceFlagForGeneratedClassName;

    @Override
    public CallInterceptionRequest postProcessRequest(CallInterceptionRequest originalRequest) {
        Optional<ExecutableElement> maybeOriginatingElement = originalRequest.getRequestExtras().getByType(OriginatingElement.class)
            .map(OriginatingElement::getElement);

        if (!maybeOriginatingElement.isPresent()) {
            return originalRequest;
        }
        ExecutableElement originatingElement = maybeOriginatingElement.get();

        boolean shouldPostProcess = AnnotationUtils.findMetaAnnotationMirror(originatingElement, interceptCallsAnnotation).isPresent();
        if (!shouldPostProcess) {
            return originalRequest;
        }

        Element enclosingElement = maybeOriginatingElement.get().getEnclosingElement();
        AnnotationUtils.findAnnotationMirror(enclosingElement, generatedClassNameProvidingAnnotation).ifPresent(annotationMirror -> {
            Optional<? extends AnnotationValue> generatedClassName = AnnotationUtils.findAnnotationValue(annotationMirror, "generatedClassName");
            generatedClassName.ifPresent(annotationValue -> originalRequest.getRequestExtras().add(produceFlagForGeneratedClassName.apply((String) annotationValue.getValue())));
        });

        return originalRequest;
    }
}
