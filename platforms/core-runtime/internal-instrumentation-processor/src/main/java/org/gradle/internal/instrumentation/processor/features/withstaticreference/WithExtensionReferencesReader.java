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

package org.gradle.internal.instrumentation.processor.features.withstaticreference;

import org.gradle.internal.instrumentation.api.annotations.features.withstaticreference.WithExtensionReferences;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.extensibility.RequestPostProcessorExtension;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils;
import org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils;
import org.gradle.internal.instrumentation.util.NameUtil;
import org.objectweb.asm.Type;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.Collections;

public class WithExtensionReferencesReader implements RequestPostProcessorExtension {
    @Override
    public Collection<CallInterceptionRequest> postProcessRequest(CallInterceptionRequest originalRequest) {
        if (shouldPostProcess(originalRequest)) {
            originalRequest.getRequestExtras().getByType(RequestExtra.OriginatingElement.class).ifPresent(originatingElement -> {
                ExecutableElement element = originatingElement.getElement();
                AnnotationUtils.findAnnotationMirror(element, WithExtensionReferences.class).ifPresent(annotation ->
                    AnnotationUtils.findAnnotationValue(annotation, "toClass").ifPresent(value -> {
                        TypeMirror typeMirror = (TypeMirror) value.getValue();
                        Type type = TypeUtils.extractType(typeMirror);
                        String methodName = extractMethodName(originalRequest, annotation);
                        originalRequest.getRequestExtras().add(new WithExtensionReferencesExtra(type, methodName));
                    }));
            });
        }
        return Collections.singletonList(originalRequest);
    }

    private static String extractMethodName(CallInterceptionRequest originalRequest, AnnotationMirror annotation) {
        return AnnotationUtils.findAnnotationValue(annotation, "methodName")
            .map(it -> (String) it.getValue())
            .filter(it -> !it.isEmpty())
            .orElse(NameUtil.interceptedJvmMethodName(originalRequest.getInterceptedCallable()));
    }

    private static boolean shouldPostProcess(CallInterceptionRequest request) {
        CallableKindInfo kind = request.getInterceptedCallable().getKind();
        return kind == CallableKindInfo.INSTANCE_METHOD || kind == CallableKindInfo.GROOVY_PROPERTY_GETTER || kind == CallableKindInfo.GROOVY_PROPERTY_SETTER;
    }
}
