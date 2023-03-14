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

package org.gradle.internal.instrumentation.extensions.property;

import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty.UpgradedGetter;
import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty.UpgradedSetter;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallInterceptionRequestImpl;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableInfoImpl;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.ImplementationInfoImpl;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterInfoImpl;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.Success;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReaderExtension.DEFAULT_VALUE_TYPE;
import static org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReaderExtension.INTERCEPTOR_JVM_DECLARATION_CLASS_NAME;
import static org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReaderExtension.getPropertyName;
import static org.gradle.internal.instrumentation.model.CallableKindInfo.INSTANCE_METHOD;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractReturnType;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractType;

@SuppressWarnings("MethodMayBeStatic")
public class PropertyUpgradeCustomAccessorsRequestReader {

    public boolean hasCustomAccessors(AnnotationMirror annotation) {
        return getCustomAccessors(annotation).isPresent();
    }

    public Collection<CallInterceptionRequestReader.Result> generateRequests(ExecutableElement method, AnnotationMirror annotationMirror) {
        TypeMirror typeMirror = getCustomAccessors(annotationMirror).orElse(null);
        if (!(typeMirror instanceof DeclaredType)) {
            throw new PropertyUpgradeCodeGenFailure(String.format("Cannot read accessors declared type for method '%s.%s'.", method.getEnclosingElement(), method));
        }

        String propertyName = getPropertyName(method);
        Type interceptedType = extractType(method.getEnclosingElement().asType());
        List<Element> upgradedMethods = ((DeclaredType) typeMirror).asElement().getEnclosedElements().stream()
            .filter(elements -> elements instanceof ExecutableElement)
            .filter(element -> isAccessorForProperty(element, propertyName))
            .collect(Collectors.toList());

        List<CallInterceptionRequest> getterRequests = upgradedMethods.stream()
            .filter(element -> element.getAnnotation(UpgradedGetter.class) != null)
            .map(m -> createJvmInterceptionRequest((ExecutableElement) m, interceptedType))
            .collect(Collectors.toList());

        List<CallInterceptionRequest> setterRequests = upgradedMethods.stream()
            .filter(element -> element.getAnnotation(UpgradedSetter.class) != null)
            .map(m -> createJvmInterceptionRequest((ExecutableElement) m, interceptedType))
            .collect(Collectors.toList());

        return Stream.of(getterRequests, setterRequests)
                .flatMap(Collection::stream)
                .map(Success::new)
                .collect(Collectors.toList());
    }

    private static Optional<TypeMirror> getCustomAccessors(AnnotationMirror annotation) {
        Optional<? extends AnnotationValue> annotationValue = AnnotationUtils.findAnnotationValue(annotation, "accessors");
        Optional<TypeMirror> type = annotationValue.map(v -> (TypeMirror) v.getValue());
        if (type.isPresent() && !extractType(type.get()).equals(DEFAULT_VALUE_TYPE)) {
            return type;
        }
        return Optional.empty();
    }

    private static boolean isAccessorForProperty(Element element, String propertyName) {
        Optional<? extends AnnotationValue> upgradedGetter = AnnotationUtils.findAnnotationValue(element, UpgradedGetter.class, "forProperty");
        if (upgradedGetter.isPresent()) {
            return upgradedGetter.get().getValue().equals(propertyName);
        }
        Optional<? extends AnnotationValue> upgradedSetter = AnnotationUtils.findAnnotationValue(element, UpgradedSetter.class, "forProperty");
        return upgradedSetter.map(annotationValue -> annotationValue.getValue().equals(propertyName)).orElse(false);
    }

    private static CallInterceptionRequest createJvmInterceptionRequest(ExecutableElement method, Type interceptedType) {
        List<RequestExtra> extras = getJvmRequestExtras(method);
        List<ParameterInfo> parameters = extractParameters(method);
        return new CallInterceptionRequestImpl(
                extractCallableInfo(INSTANCE_METHOD, method, interceptedType, parameters),
                extractImplementationInfo(method, parameters),
                extras
        );
    }

    private static CallableInfo extractCallableInfo(CallableKindInfo kindInfo, ExecutableElement methodElement, Type interceptedType, List<ParameterInfo> parameters) {
        Type returnType = extractReturnType(methodElement);
        String originalMethodName = methodElement.getSimpleName().toString().replace("access_", "");
        return new CallableInfoImpl(kindInfo, interceptedType, originalMethodName, returnType, parameters);
    }

    private static List<ParameterInfo> extractParameters(ExecutableElement element) {
        List<ParameterInfo> parameters = new ArrayList<>();
        for (int i = 0; i < element.getParameters().size(); i++) {
            VariableElement parameter = element.getParameters().get(i);
            Type parameterType = extractType(parameter.asType());
            ParameterKindInfo parameterKindInfo = i == 0 ? ParameterKindInfo.RECEIVER : ParameterKindInfo.METHOD_PARAMETER;
            parameters.add(new ParameterInfoImpl(parameter.getSimpleName().toString(), parameterType, parameterKindInfo));
        }
        return parameters;
    }

    private static ImplementationInfoImpl extractImplementationInfo(ExecutableElement method, List<ParameterInfo> parameters) {
        Type implementationOwner = extractType(method.getEnclosingElement().asType());
        String implementationName = method.getSimpleName().toString();
        Type returnType = extractReturnType(method);
        String implementationDescriptor = Type.getMethodDescriptor(returnType, toArray(parameters));
        return new ImplementationInfoImpl(implementationOwner, implementationName, implementationDescriptor);
    }

    private static Type[] toArray(List<ParameterInfo> parameters) {
        Type[] array = new Type[parameters.size()];
        int i = 0;
        for (ParameterInfo parameter : parameters) {
            array[i++] = parameter.getParameterType();
        }
        return array;
    }

    @NotNull
    private static List<RequestExtra> getJvmRequestExtras(ExecutableElement method) {
        List<RequestExtra> extras = new ArrayList<>();
        extras.add(new RequestExtra.OriginatingElement(method));
        extras.add(new RequestExtra.InterceptJvmCalls(INTERCEPTOR_JVM_DECLARATION_CLASS_NAME));
        return extras;
    }
}
