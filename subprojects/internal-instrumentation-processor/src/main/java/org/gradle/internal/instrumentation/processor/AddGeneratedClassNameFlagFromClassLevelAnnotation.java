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
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class AddGeneratedClassNameFlagFromClassLevelAnnotation implements RequestPostProcessorExtension {
    public AddGeneratedClassNameFlagFromClassLevelAnnotation(
        Predicate<? super CallInterceptionRequest> shouldAddExtraToRequestPredicate,
        Class<? extends Annotation> generatedClassNameProvidingAnnotation,
        Function<String, RequestExtra> produceFlagForGeneratedClassName
    ) {
        this.shouldAddExtraToRequestPredicate = shouldAddExtraToRequestPredicate;

        this.generatedClassNameProvidingAnnotation = generatedClassNameProvidingAnnotation;
        this.produceFlagForGeneratedClassName = produceFlagForGeneratedClassName;
    }

    private final Predicate<? super CallInterceptionRequest> shouldAddExtraToRequestPredicate;
    private final Class<? extends Annotation> generatedClassNameProvidingAnnotation;
    private final Function<String, RequestExtra> produceFlagForGeneratedClassName;

    @Override
    public Collection<CallInterceptionRequest> postProcessRequest(CallInterceptionRequest originalRequest) {
        Optional<ExecutableElement> maybeOriginatingElement = originalRequest.getRequestExtras().getByType(OriginatingElement.class)
            .map(OriginatingElement::getElement);

        if (!maybeOriginatingElement.isPresent()) {
            return Collections.singletonList(originalRequest);
        }

        boolean shouldPostProcess = shouldAddExtraToRequestPredicate.test(originalRequest);
        if (!shouldPostProcess) {
            return Collections.singletonList(originalRequest);
        }

        Element enclosingElement = maybeOriginatingElement.get().getEnclosingElement();
        AnnotationUtils.findAnnotationMirror(enclosingElement, generatedClassNameProvidingAnnotation).ifPresent(annotationMirror -> {
            Optional<? extends AnnotationValue> generatedClassName = AnnotationUtils.findAnnotationValue(annotationMirror, "generatedClassName");
            generatedClassName.ifPresent(annotationValue -> originalRequest.getRequestExtras().add(produceFlagForGeneratedClassName.apply((String) annotationValue.getValue())));
        });

        return Collections.singletonList(originalRequest);
    }

    public static Predicate<CallInterceptionRequest> ifHasAnnotation(Class<? extends Annotation> annotationType) {
        return request -> {
            Optional<ExecutableElement> maybeOriginatingElement = request.getRequestExtras()
                .getByType(OriginatingElement.class)
                .map(OriginatingElement::getElement);

            if (!maybeOriginatingElement.isPresent()) {
                return false;
            }

            ExecutableElement originatingElement = maybeOriginatingElement.get();
            return AnnotationUtils.findMetaAnnotationMirror(originatingElement, annotationType).isPresent();
        };
    }

    public static Predicate<CallInterceptionRequest> ifHasExtraOfType(Class<? extends RequestExtra> extraType) {
        return request -> request.getRequestExtras().getByType(extraType).isPresent();
    }
}
