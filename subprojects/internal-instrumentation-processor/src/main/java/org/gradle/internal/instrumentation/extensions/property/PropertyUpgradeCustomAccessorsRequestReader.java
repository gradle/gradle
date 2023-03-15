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
import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty.UpgradedGroovyProperty;
import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty.UpgradedSetter;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallInterceptionRequestImpl;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableInfoImpl;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.CallableOwnerInfo;
import org.gradle.internal.instrumentation.model.CallableReturnTypeInfo;
import org.gradle.internal.instrumentation.model.ImplementationInfoImpl;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterInfoImpl;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.Success;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils;
import org.objectweb.asm.Type;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReaderExtension.DEFAULT_VALUE_TYPE;
import static org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReaderExtension.INTERCEPTOR_GROOVY_DECLARATION_CLASS_NAME;
import static org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReaderExtension.INTERCEPTOR_JVM_DECLARATION_CLASS_NAME;
import static org.gradle.internal.instrumentation.model.CallableKindInfo.GROOVY_PROPERTY;
import static org.gradle.internal.instrumentation.model.CallableKindInfo.INSTANCE_METHOD;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractReturnType;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractType;

@SuppressWarnings("MethodMayBeStatic")
class PropertyUpgradeCustomAccessorsRequestReader {

    public boolean hasCustomAccessors(AnnotationMirror annotation) {
        return getCustomAccessors(annotation).isPresent();
    }

    public Collection<CallInterceptionRequestReader.Result> readRequests(String propertyName, ExecutableElement method, AnnotationMirror annotationMirror) {
        TypeMirror typeMirror = getCustomAccessors(annotationMirror).orElse(null);
        if (!(typeMirror instanceof DeclaredType)) {
            throw new PropertyUpgradeCodeGenFailure(String.format("Cannot read accessors declared type for method '%s.%s'.", method.getEnclosingElement(), method));
        }

        Type interceptedType = extractType(method.getEnclosingElement().asType());
        List<ExecutableElement> upgradedMethods = ((DeclaredType) typeMirror).asElement().getEnclosedElements().stream()
            .filter(elements -> elements instanceof ExecutableElement)
            .map(element -> (ExecutableElement) element)
            .filter(element -> isAccessorForProperty(element, propertyName))
            .collect(Collectors.toList());

        validate(upgradedMethods, method);

        List<CallInterceptionRequest> upgradedGroovyPropertyRequests = upgradedMethods.stream()
            .filter(element -> element.getAnnotation(UpgradedGroovyProperty.class) != null)
            .map(m -> createGroovyPropertyInterceptionRequest(propertyName, m, interceptedType))
            .collect(Collectors.toList());

        List<CallInterceptionRequest> getterRequests = upgradedMethods.stream()
            .filter(element -> element.getAnnotation(UpgradedGetter.class) != null)
            .map(m -> createJvmInterceptionRequest(m, interceptedType))
            .collect(Collectors.toList());

        List<CallInterceptionRequest> setterRequests = upgradedMethods.stream()
            .filter(element -> element.getAnnotation(UpgradedSetter.class) != null)
            .map(m -> createJvmInterceptionRequest(m, interceptedType))
            .collect(Collectors.toList());

        return Stream.of(upgradedGroovyPropertyRequests, getterRequests, setterRequests)
                .flatMap(Collection::stream)
                .map(Success::new)
                .collect(Collectors.toList());
    }

    private static void validate(List<ExecutableElement> upgradedMethods, ExecutableElement propertyMethod) {
        String propertyDisplayName = String.format("%s.%s", propertyMethod.getEnclosingElement(), propertyMethod);
        Set<String> validationProblems = new LinkedHashSet<>();
        if (upgradedMethods.isEmpty()) {
            validationProblems.add("No custom accessors found for property: " + propertyDisplayName + ".");
        }

        Element propertyMethodEnclosingElement = propertyMethod.getEnclosingElement();
        upgradedMethods.forEach(method -> {
            if (!method.getSimpleName().toString().startsWith("access_")) {
                validationProblems.add(String.format("Accessor method '%s.%s' name should start with 'access_'.", method.getEnclosingElement(), method));
            }
            if (method.getParameters().isEmpty()) {
                validationProblems.add(String.format("First parameter for accessor method '%s.%s' should be of type '%s', but this method has no parameter.", method.getEnclosingElement(), method, propertyMethodEnclosingElement));
            } else if (!propertyMethodEnclosingElement.asType().equals(method.getParameters().get(0).asType())) {
                validationProblems.add(String.format("First parameter for accessor method '%s.%s' should be of type '%s', but is '%s'.", method.getEnclosingElement(), method, propertyMethodEnclosingElement, method.getParameters().get(0).asType()));
            }
            if (method.getAnnotation(UpgradedGetter.class) != null && method.getAnnotation(UpgradedSetter.class) != null) {
                validationProblems.add(String.format("Accessor method '%s.%s' should not have have @UpgradedGetter and @UpgradedSetter annotation.", method.getEnclosingElement(), method));
            }
            if (method.getAnnotation(UpgradedGroovyProperty.class) != null && method.getAnnotation(UpgradedSetter.class) != null) {
                validationProblems.add(String.format("Accessor method '%s.%s' should not have have @UpgradedGroovyProperty and @UpgradedSetter annotation.", method.getEnclosingElement(), method));
            }
        });

        long groovyPropertyUpgradesCount = upgradedMethods.stream()
            .filter(element -> element.getAnnotation(UpgradedGroovyProperty.class) != null)
            .count();
        if (groovyPropertyUpgradesCount <= 0) {
            validationProblems.add("No accessors annotated with @UpgradedGroovyProperty for property: " + propertyDisplayName + ". There should be 1 accessor with that annotation.");
        } else if (groovyPropertyUpgradesCount > 1) {
            validationProblems.add("Too many accessors annotated with @UpgradedGroovyProperty for property: " + propertyDisplayName + ". There should be just 1, but there are: '" + groovyPropertyUpgradesCount + "'.");
        }

        long getterUpgradesCount = upgradedMethods.stream()
            .filter(element -> element.getAnnotation(UpgradedGetter.class) != null)
            .count();
        if (getterUpgradesCount <= 0) {
            validationProblems.add("No accessors annotated with @UpgradedGetter for property: " + propertyDisplayName + ". There should be at least 1 accessor with that annotation.");
        }

        List<ExecutableElement> getterOrGroovyPropertyUpgrades = upgradedMethods.stream()
            .filter(element -> element.getAnnotation(UpgradedGetter.class) != null || element.getAnnotation(UpgradedGroovyProperty.class) != null)
            .collect(Collectors.toList());
        getterOrGroovyPropertyUpgrades.stream()
            .filter(method -> extractType(method.getReturnType()).equals(Type.VOID_TYPE))
            .forEach(method -> validationProblems.add(String.format("Accessor method '%s.%s' annotated with @UpgradedGroovyProperty or @UpgradedGetter should not have return type 'void'.", method.getEnclosingElement(), method)));
        getterOrGroovyPropertyUpgrades.stream()
            .filter(method -> method.getParameters().size() > 1)
            .forEach(method -> validationProblems.add(String.format("Too many parameters for accessor method '%s.%s' annotated with @UpgradedGroovyProperty or @UpgradedGetter. There should be just 1 parameter of type '%s', but this method has also additional parameters.", method.getEnclosingElement(), method, propertyMethodEnclosingElement)));

        if (!validationProblems.isEmpty()) {
            throw new PropertyUpgradeCodeGenFailure(validationProblems);
        }
    }

    private static Optional<TypeMirror> getCustomAccessors(AnnotationMirror annotation) {
        Optional<? extends AnnotationValue> annotationValue = AnnotationUtils.findAnnotationValue(annotation, "accessors");
        Optional<TypeMirror> type = annotationValue.map(v -> (TypeMirror) v.getValue());
        if (type.isPresent() && !extractType(type.get()).equals(DEFAULT_VALUE_TYPE)) {
            return type;
        }
        return Optional.empty();
    }

    private static boolean isAccessorForProperty(ExecutableElement method, String propertyName) {
        Set<String> forPropertyValues = new HashSet<>();
        Optional<? extends AnnotationValue> upgradedGetter = AnnotationUtils.findAnnotationValue(method, UpgradedGetter.class, "forProperty");
        upgradedGetter.ifPresent(forPropertyValue -> forPropertyValues.add((String) forPropertyValue.getValue()));
        Optional<? extends AnnotationValue> upgradedProperty = AnnotationUtils.findAnnotationValue(method, UpgradedGroovyProperty.class, "forProperty");
        upgradedProperty.ifPresent(forPropertyValue -> forPropertyValues.add((String) forPropertyValue.getValue()));
        Optional<? extends AnnotationValue> upgradedSetter = AnnotationUtils.findAnnotationValue(method, UpgradedSetter.class, "forProperty");
        upgradedSetter.ifPresent(forPropertyValue -> forPropertyValues.add((String) forPropertyValue.getValue()));
        if (forPropertyValues.size() > 1) {
            throw new PropertyUpgradeCodeGenFailure(String.format("Accessor method '%s.%s' is used multiple different properties. That use case is not supported.", method.getEnclosingElement(), method));
        }
        return forPropertyValues.contains(propertyName);
    }

    private static CallInterceptionRequest createGroovyPropertyInterceptionRequest(String propertyName, ExecutableElement method, Type interceptedType) {
        List<RequestExtra> extras = Arrays.asList(new RequestExtra.OriginatingElement(method), new RequestExtra.InterceptGroovyCalls(INTERCEPTOR_GROOVY_DECLARATION_CLASS_NAME));
        List<ParameterInfo> parameters = extractParameters(method);
        Type returnType = extractReturnType(method);
        CallableOwnerInfo owner = new CallableOwnerInfo(interceptedType, true);
        CallableReturnTypeInfo returnTypeInfo = new CallableReturnTypeInfo(returnType);
        return new CallInterceptionRequestImpl(
            new CallableInfoImpl(GROOVY_PROPERTY, owner, propertyName, returnTypeInfo, parameters),
            extractImplementationInfo(method, parameters),
            extras
        );
    }

    private static CallInterceptionRequest createJvmInterceptionRequest(ExecutableElement method, Type interceptedType) {
        List<RequestExtra> extras = new ArrayList<>();
        extras.add(new RequestExtra.OriginatingElement(method));
        extras.add(new RequestExtra.InterceptJvmCalls(INTERCEPTOR_JVM_DECLARATION_CLASS_NAME));
        List<ParameterInfo> parameters = extractParameters(method);
        return new CallInterceptionRequestImpl(
            extractCallableInfo(INSTANCE_METHOD, method, interceptedType, parameters),
            extractImplementationInfo(method, parameters),
            extras
        );
    }

    private static CallableInfo extractCallableInfo(CallableKindInfo kindInfo, ExecutableElement methodElement, Type interceptedType, List<ParameterInfo> parameters) {
        CallableOwnerInfo owner = new CallableOwnerInfo(interceptedType, true);
        CallableReturnTypeInfo returnTypeInfo = new CallableReturnTypeInfo(extractReturnType(methodElement));
        String originalMethodName = methodElement.getSimpleName().toString().replace("access_", "");
        return new CallableInfoImpl(kindInfo, owner, originalMethodName, returnTypeInfo, parameters);
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

}
