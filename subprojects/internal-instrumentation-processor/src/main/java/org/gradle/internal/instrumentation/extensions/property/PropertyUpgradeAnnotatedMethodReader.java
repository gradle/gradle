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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallInterceptionRequestImpl;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableInfoImpl;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.ImplementationInfoImpl;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterInfoImpl;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.extensibility.AnnotatedMethodReaderExtension;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.InvalidRequest;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.Success;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.gradle.internal.instrumentation.model.ParameterKindInfo.METHOD_PARAMETER;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractType;

public class PropertyUpgradeAnnotatedMethodReader implements AnnotatedMethodReaderExtension {

    private static final Type PROPERTY = Type.getType(Property.class);
    private static final Type REGULAR_FILE_PROPERTY = Type.getType(RegularFileProperty.class);
    private static final Type DIRECTORY_PROPERTY = Type.getType(DirectoryProperty.class);
    private static final Type DEFAULT_TYPE = Type.getType(UpgradedProperty.SameAsGenericType.class);
    // TODO: Should be read from an annotation
    private static final String INTERCEPTOR_DECLARATION_CLASS_NAME = "org.gradle.internal.classpath.InterceptorDeclaration_JvmBytecodeImplPropertyUpgrades";

    @Override
    public Collection<Result> readRequest(ExecutableElement input) {
        Optional<? extends AnnotationMirror> annotation = AnnotationUtils.findAnnotationMirror(input, UpgradedProperty.class);
        if (!annotation.isPresent()) {
            return Collections.emptySet();
        }

        try {
            Type originalType = extractOriginalType(input, annotation.get());
            CallInterceptionRequest getterRequest = createJvmGetterInterceptionRequest(input, originalType);
            CallInterceptionRequest setterRequest = createJvmSetterInterceptionRequest(input, originalType);
            return Arrays.asList(new Success(getterRequest), new Success(setterRequest));
        } catch (AnnotationReadFailure failure) {
            return Collections.singletonList(new InvalidRequest(failure.reason));
        }
    }

    private static Type extractOriginalType(ExecutableElement method, AnnotationMirror annotation) {
        Optional<? extends AnnotationValue> typeMirrorOptional = AnnotationUtils.findAnnotationValue(annotation, "originalType");
        if (!typeMirrorOptional.isPresent()) {
            throw new AnnotationReadFailure("Can't read @UpgradedProperty#orignalType for " + method.getSimpleName());
        }
        TypeMirror typeMirror = (TypeMirror) typeMirrorOptional.get().getValue();
        Type type = extractType(typeMirror);
        if (type != DEFAULT_TYPE) {
            return type;
        }
        return extractOriginalTypeFromGeneric(method, method.getReturnType());
    }

    private static Type extractOriginalTypeFromGeneric(ExecutableElement method, TypeMirror typeMirror) {
        throw new AnnotationReadFailure("Reading generic types from lazy properties is not yet implemented, set originalType for now." + method.getSimpleName());
    }

    private static CallInterceptionRequest createJvmGetterInterceptionRequest(ExecutableElement method, Type originalType) {
        List<RequestExtra> extras = getJvmRequestExtras(method);
        return new CallInterceptionRequestImpl(
            extractCallableInfo(method, originalType, method.getSimpleName().toString(), Collections.emptyList()),
            extractImplementationInfo(method, originalType, "get", Collections.emptyList()),
            extras
        );
    }

    private static CallInterceptionRequest createJvmSetterInterceptionRequest(ExecutableElement method, Type originalType) {
        Type returnType = Type.VOID_TYPE;
        String callableName = method.getSimpleName().toString().replaceFirst("get", "set");
        List<ParameterInfo> parameters = Collections.singletonList(new ParameterInfoImpl("arg0", originalType, METHOD_PARAMETER));
        List<RequestExtra> extras = getJvmRequestExtras(method);
        return new CallInterceptionRequestImpl(
            extractCallableInfo(method, returnType, callableName, parameters),
            extractImplementationInfo(method, returnType, "set", parameters),
            extras
        );
    }

    @NotNull
    private static List<RequestExtra> getJvmRequestExtras(ExecutableElement method) {
        List<RequestExtra> extras = new ArrayList<>();
        extras.add(new RequestExtra.OriginatingElement(method));
        extras.add(new RequestExtra.InterceptJvmCalls(INTERCEPTOR_DECLARATION_CLASS_NAME));
        String implementationClass = getGeneratedClassName(method.getEnclosingElement());
        extras.add(new PropertyUpgradeRequestExtra(implementationClass, method.getSimpleName().toString()));
        return extras;
    }

    private static CallableInfo extractCallableInfo(ExecutableElement methodElement, Type returnType, String callableName, List<ParameterInfo> parameter) {
        CallableKindInfo kindInfo = CallableKindInfo.INSTANCE_METHOD;
        Type owner = extractType(methodElement.getEnclosingElement().asType());
        return new CallableInfoImpl(kindInfo, owner, callableName, returnType, parameter);
    }

    private static ImplementationInfoImpl extractImplementationInfo(ExecutableElement method, Type returnType, String methodPrefix, List<ParameterInfo> parameters) {
        Type owner = extractType(method.getEnclosingElement().asType());
        Type implementationOwner = Type.getObjectType(getGeneratedClassName(method.getEnclosingElement()));
        String implementationName = getImplementationMethodName(method, methodPrefix);
        String implementationDescriptor = Type.getMethodDescriptor(returnType, toArray(owner, parameters));
        return new ImplementationInfoImpl(implementationOwner, implementationName, implementationDescriptor);
    }

    private static String getGeneratedClassName(Element originalType) {
        return "org.gradle.internal.instrumentation." + originalType.getSimpleName() + "_Adapter";
    }

    private static Type[] toArray(Type owner, List<ParameterInfo> parameters) {
        Type[] array = new Type[1 + parameters.size()];
        array[0] = owner;
        int i = 1;
        for (ParameterInfo parameter : parameters) {
            array[i++] = parameter.getParameterType();
        }
        return array;
    }

    private static String getImplementationMethodName(ExecutableElement method, String methodPrefix) {
        String property = method.getSimpleName().toString().replace("get", "");
        property = Character.toLowerCase(property.charAt(0)) + property.substring(1);
        return "access_" + methodPrefix + "_" + property;
    }

    // TODO Consolidate with AnnotationCallInterceptionRequestReaderImpl#Failure
    private static class AnnotationReadFailure extends RuntimeException {
        final String reason;

        private AnnotationReadFailure(String reason) {
            this.reason = reason;
        }
    }
}
