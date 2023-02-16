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

package org.gradle.internal.instrumentation.processor.modelreader;

import org.gradle.internal.Cast;
import org.gradle.internal.instrumentation.api.annotations.CallableDefinition;
import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.InterceptGroovyCalls;
import org.gradle.internal.instrumentation.api.annotations.InterceptJvmCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.model.CallInterceptionRequestImpl;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableInfoImpl;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterInfoImpl;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;
import org.gradle.internal.instrumentation.model.RequestFlag;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.processor.modelreader.AnnotationUtils.collectMetaAnnotationTypes;
import static org.gradle.internal.instrumentation.processor.modelreader.AnnotationUtils.findAnnotationMirror;
import static org.gradle.internal.instrumentation.processor.modelreader.AnnotationUtils.findAnnotationValue;
import static org.gradle.internal.instrumentation.processor.modelreader.TypeUtils.extractMethodDescriptor;
import static org.gradle.internal.instrumentation.processor.modelreader.TypeUtils.extractReturnType;
import static org.gradle.internal.instrumentation.processor.modelreader.TypeUtils.extractType;

public class AnnotationCallInterceptionRequestReaderImpl implements AnnotationCallInterceptionRequestReader {

    @Override
    public Result readRequest(ExecutableElement input) {
        if (input.getKind() != ElementKind.METHOD) {
            return new Result.RequestNotFound();
        }

        if (!input.getModifiers().containsAll(Arrays.asList(Modifier.STATIC, Modifier.PUBLIC))) {
            return new Result.RequestNotFound();
        }

        try {
            Type implementationOwner = extractType(input.getEnclosingElement().asType());
            String implementationName = input.getSimpleName().toString();
            String implementationDescriptor = extractMethodDescriptor(input);

            List<RequestFlag> requestFlags = extractRequestFlags(input);
            if (requestFlags.isEmpty()) {
                return new Result.InvalidRequest("Found a request with empty flags (no instruction provided on how to intercept the calls)");
            }

            CallableInfo callableInfo = extractCallableInfo(input);

            return new Result.Success(new CallInterceptionRequestImpl(input, callableInfo, implementationOwner, implementationName, implementationDescriptor, requestFlags));
        } catch (Failure e) {
            return new Result.InvalidRequest(e.reason);
        }
    }

    private static List<RequestFlag> extractRequestFlags(ExecutableElement methodElement) {
        Set<TypeElement> metaAnnotations = collectMetaAnnotationTypes(methodElement);
        List<RequestFlag> results = new ArrayList<>();

        Element classElement = methodElement.getEnclosingElement();

        boolean isInterceptJvmCalls = metaAnnotations.stream().anyMatch(it -> it.asType().toString().equals(InterceptJvmCalls.class.getCanonicalName()));
        if (isInterceptJvmCalls) {
            Optional<? extends AnnotationMirror> interceptJvmCallsAnnotation = findAnnotationMirror(classElement, SpecificJvmCallInterceptors.class);
            Optional<? extends AnnotationValue> generatedClassNameValue = interceptJvmCallsAnnotation.flatMap(annotationMirror -> findAnnotationValue(annotationMirror, "generatedClassName"));
            String generatedClassName = (String) generatedClassNameValue.orElseThrow(() -> new Failure("missing annotation value")).getValue();
            results.add(new RequestFlag.InterceptJvmCalls(generatedClassName));
        }
        boolean isInterceptGroovyCalls = metaAnnotations.stream().anyMatch(it -> it.asType().toString().equals(InterceptGroovyCalls.class.getCanonicalName()));
        if (isInterceptGroovyCalls) {
            Optional<? extends AnnotationMirror> interceptGroovyCallsAnnotation = findAnnotationMirror(classElement, SpecificGroovyCallInterceptors.class);
            Optional<? extends AnnotationValue> generatedClassNameValue = interceptGroovyCallsAnnotation.flatMap(annotationMirror -> AnnotationUtils.findAnnotationValue(annotationMirror, "generatedClassName"));
            String generatedClassName = (String) generatedClassNameValue.orElseThrow(() -> new Failure("missing annotation value")).getValue();
            results.add(new RequestFlag.InterceptGroovyCalls(generatedClassName));
        }
        return results;

    }

    private static CallableInfo extractCallableInfo(ExecutableElement methodElement) {
        CallableKindInfo kindInfo = extractCallableKind(methodElement);
        Type owner = extractOwnerClass(methodElement);
        String callableName = getCallableName(methodElement, kindInfo);
        Type returnType = extractReturnType(methodElement);
        List<ParameterInfo> parameterInfos = extractParameters(methodElement);
        return new CallableInfoImpl(kindInfo, owner, callableName, returnType, parameterInfos);
    }

    private static String getCallableName(ExecutableElement methodElement, CallableKindInfo kindInfo) {
        CallableDefinition.Name nameAnnotation = methodElement.getAnnotation(CallableDefinition.Name.class);
        String nameFromPattern = callableNameFromNamingConvention(methodElement.getSimpleName().toString());
        if (kindInfo == CallableKindInfo.AFTER_CONSTRUCTOR) {
            if (nameAnnotation != null) {
                throw new Failure("@" + CallableKind.AfterConstructor.class.getSimpleName() + " cannot be used with @" + CallableDefinition.Name.class.getSimpleName());
            }
            if (nameFromPattern != null) {
                throw new Failure("Constructor interceptors cannot follow the 'intercept_*' name pattern");
            }
            return "<init>";
        } else {
            if (nameAnnotation == null) {
                if (nameFromPattern == null) {
                    throw new Failure("Expected the interceptor method to be annotated with @" + CallableDefinition.Name.class.getSimpleName() + " or to have the 'intercept_*' pattern in the name");
                }
                return nameFromPattern;
            } else {
                if (nameFromPattern != null) {
                    throw new Failure("@" + CallableDefinition.Name.class.getSimpleName() + " cannot be used with method names following the 'intercept_*' pattern");
                }
            }
            return nameAnnotation.value();
        }
    }

    @Nullable
    private static String callableNameFromNamingConvention(String methodName) {
        if (!isInterceptPatternName(methodName)) {
            return null;
        }
        return methodName.replace(INTERCEPT_PREFIX, "");
    }

    private static final String INTERCEPT_PREFIX = "intercept_";

    private static boolean isInterceptPatternName(String methodName) {
        return methodName.startsWith(INTERCEPT_PREFIX);
    }

    private static CallableKindInfo extractCallableKind(ExecutableElement methodElement) {
        List<Annotation> kindAnnotations = Stream.of(callableKindAnnotationClasses)
            .map(methodElement::getAnnotation)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (kindAnnotations.size() > 1) {
            throw new Failure("More than one callable kind annotations present: " + kindAnnotations);
        } else if (kindAnnotations.size() == 0) {
            throw new Failure("No callable kind annotation specified, expected one of " + Arrays.stream(callableKindAnnotationClasses).map(Class::getSimpleName).collect(Collectors.joining(", ")));
        } else {
            return CallableKindInfo.fromAnnotation(kindAnnotations.get(0));
        }
    }

    private static List<ParameterInfo> extractParameters(ExecutableElement methodElement) {
        return methodElement.getParameters().stream()
            .map(AnnotationCallInterceptionRequestReaderImpl::extractParameter)
            .collect(Collectors.toList());
    }

    private static ParameterInfo extractParameter(VariableElement parameterElement) {
        Type parameterType = extractType(parameterElement.asType());
        ParameterKindInfo parameterKindInfo = extractParameterKind(parameterElement);
        return new ParameterInfoImpl(parameterElement.getSimpleName().toString(), parameterType, parameterKindInfo);
    }

    private static ParameterKindInfo extractParameterKind(VariableElement parameterElement) {
        List<Annotation> kindAnnotations = Stream.of(parameterKindAnnotationClasses)
            .map(parameterElement::getAnnotation)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (kindAnnotations.size() > 1) {
            throw new Failure("More than one parameter kind annotations present: " + kindAnnotations);
        } else if (kindAnnotations.size() == 0) {
            return ParameterKindInfo.METHOD_PARAMETER;
        } else {
            return ParameterKindInfo.fromAnnotation(kindAnnotations.get(0));
        }
    }

    private static Type extractOwnerClass(ExecutableElement executableElement) {
        Optional<? extends AnnotationMirror> maybeStaticMethod = findAnnotationMirror(executableElement, CallableKind.StaticMethod.class);
        List<VariableElement> receivers = executableElement.getParameters().stream()
            .filter(it -> it.getAnnotation(ParameterKind.Receiver.class) != null)
            .collect(Collectors.toList());

        if (maybeStaticMethod.isPresent()) {
            if (receivers.size() > 0) {
                throw new Failure("Static method interceptors should not declare @" + ParameterKind.Receiver.class.getSimpleName() + " parameters");
            }
            TypeMirror staticMethodOwner = (TypeMirror) findAnnotationValue(maybeStaticMethod.get(), "ofClass").orElseThrow(() -> new IllegalStateException("missing annotation value")).getValue();
            return extractType(staticMethodOwner);
        }

        if (receivers.size() == 0) {
            throw new Failure("Expected owner defined as a @" + ParameterKind.Receiver.class.getSimpleName() + " parameter or @" + CallableKind.StaticMethod.class.getSimpleName() + " annotation");
        }
        if (receivers.size() > 1) {
            throw new Failure("Only one parameter can be annotated with @" + ParameterKind.Receiver.class.getSimpleName());
        }
        VariableElement receiver = receivers.get(0);
        TypeMirror receiverType = receiver.asType();
        if (receiverType.getKind() != TypeKind.DECLARED) {
            throw new Failure("Receiver should be a class or interface, got " + receiverType);
        }
        return extractType(receiverType);
    }

    private static class Failure extends RuntimeException {
        final String reason;

        private Failure(String reason) {
            this.reason = reason;
        }
    }

    private static final Class<? extends Annotation>[] callableKindAnnotationClasses = Cast.uncheckedNonnullCast(Stream.of(
        CallableKind.InstanceMethod.class,
        CallableKind.StaticMethod.class,
        CallableKind.AfterConstructor.class,
        CallableKind.GroovyProperty.class
    ).toArray(Class[]::new));

    private static final Class<? extends Annotation>[] parameterKindAnnotationClasses = Cast.uncheckedNonnullCast(Stream.of(
        ParameterKind.Receiver.class,
        ParameterKind.CallerClassName.class,
        ParameterKind.KotlinDefaultMask.class
    ).toArray(Class[]::new));
}
