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

package org.gradle.internal.instrumentation.processor.modelreader.impl;

import org.gradle.internal.Cast;
import org.gradle.internal.instrumentation.api.annotations.CallableDefinition;
import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.InterceptInherited;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind;
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
import org.gradle.internal.instrumentation.model.RequestExtra.OriginatingElement;
import org.gradle.internal.instrumentation.processor.extensibility.AnnotatedMethodReaderExtension;
import org.objectweb.asm.Type;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils.findAnnotationMirror;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils.findAnnotationValue;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractMethodDescriptor;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractReturnType;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractType;

public class AnnotationCallInterceptionRequestReaderImpl implements AnnotatedMethodReaderExtension {

    @Override
    public Collection<Result> readRequest(ExecutableElement input) {
        if (input.getKind() != ElementKind.METHOD) {
            return emptyList();
        }

        if (!input.getModifiers().containsAll(Arrays.asList(Modifier.STATIC, Modifier.PUBLIC))) {
            return emptyList();
        }

        try {
            CallableInfo callableInfo = extractCallableInfo(input);
            ImplementationInfoImpl implementationInfo = extractImplementationInfo(input);
            List<RequestExtra> requestExtras = Collections.singletonList(new OriginatingElement(input));
            return singletonList(new Result.Success(new CallInterceptionRequestImpl(callableInfo, implementationInfo, requestExtras)));
        } catch (Failure e) {
            return singletonList(new Result.InvalidRequest(e.reason));
        }
    }

    private static CallableInfo extractCallableInfo(ExecutableElement methodElement) {
        CallableKindInfo kindInfo = extractCallableKind(methodElement);
        Type ownerType = extractOwnerClass(methodElement);
        boolean interceptInherited = isInterceptInherited(methodElement);
        CallableOwnerInfo owner = new CallableOwnerInfo(ownerType, interceptInherited);
        String callableName = getCallableName(methodElement, kindInfo);
        CallableReturnTypeInfo returnType = new CallableReturnTypeInfo(extractReturnType(methodElement));
        List<ParameterInfo> parameterInfos = extractParameters(methodElement);
        return new CallableInfoImpl(kindInfo, owner, callableName, returnType, parameterInfos);
    }

    @Nonnull
    private static ImplementationInfoImpl extractImplementationInfo(ExecutableElement input) {
        Type implementationOwner = extractType(input.getEnclosingElement().asType());
        String implementationName = input.getSimpleName().toString();
        String implementationDescriptor = extractMethodDescriptor(input);
        return new ImplementationInfoImpl(implementationOwner, implementationName, implementationDescriptor);
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
        List<Annotation> kindAnnotations = Stream.of(CALLABLE_KIND_ANNOTATION_CLASSES)
            .map(methodElement::getAnnotation)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (kindAnnotations.size() > 1) {
            throw new Failure("More than one callable kind annotations present: " + kindAnnotations);
        } else if (kindAnnotations.size() == 0) {
            throw new Failure("No callable kind annotation specified, expected one of " + Arrays.stream(CALLABLE_KIND_ANNOTATION_CLASSES).map(Class::getSimpleName).collect(Collectors.joining(", ")));
        } else {
            return CallableKindInfo.fromAnnotation(kindAnnotations.get(0));
        }
    }

    private static boolean isInterceptInherited(ExecutableElement methodElement) {
        return methodElement.getAnnotation(InterceptInherited.class) != null;
    }

    private static List<ParameterInfo> extractParameters(ExecutableElement methodElement) {
        List<ParameterInfo> list = new ArrayList<>();
        List<? extends VariableElement> parameters = methodElement.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement variableElement = parameters.get(i);
            boolean isVararg = methodElement.isVarArgs() && i == parameters.size() - 1;
            ParameterInfo parameterInfo = extractParameter(variableElement, isVararg);
            list.add(parameterInfo);
        }
        return list;
    }

    private static ParameterInfo extractParameter(VariableElement parameterElement, boolean isVararg) {
        Type parameterType = extractType(parameterElement.asType());
        ParameterKindInfo parameterKindInfo = extractParameterKind(parameterElement, isVararg);

        if (parameterKindInfo == ParameterKindInfo.VARARG_METHOD_PARAMETER) {
            if (parameterType.getSort() != Type.ARRAY) {
                throw new Failure("a @" + ParameterKind.VarargParameter.class.getSimpleName() + " parameter must have an array type");
            }
        }

        return new ParameterInfoImpl(parameterElement.getSimpleName().toString(), parameterType, parameterKindInfo);
    }

    private static ParameterKindInfo extractParameterKind(VariableElement parameterElement, boolean isVararg) {
        List<Annotation> kindAnnotations = Stream.of(PARAMETER_KIND_ANNOTATION_CLASSES)
            .map(parameterElement::getAnnotation)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (kindAnnotations.size() > 1) {
            throw new Failure("More than one parameter kind annotations present: " + kindAnnotations);
        } else if (kindAnnotations.size() == 0) {
            return isVararg ? ParameterKindInfo.VARARG_METHOD_PARAMETER : ParameterKindInfo.METHOD_PARAMETER;
        } else {
            ParameterKindInfo parameterKindInfo = ParameterKindInfo.fromAnnotation(kindAnnotations.get(0));
            if (isVararg && parameterKindInfo != ParameterKindInfo.VARARG_METHOD_PARAMETER) {
                throw new Failure("a vararg parameter can only be @" + ParameterKind.VarargParameter.class.getSimpleName() + " (maybe implicitly)");
            }
            return parameterKindInfo;
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

    private static final Class<? extends Annotation>[] CALLABLE_KIND_ANNOTATION_CLASSES = Cast.uncheckedNonnullCast(new Class<?>[]{
        CallableKind.InstanceMethod.class,
        CallableKind.StaticMethod.class,
        CallableKind.AfterConstructor.class,
        CallableKind.GroovyPropertyGetter.class,
        CallableKind.GroovyPropertySetter.class
    });

    private static final Class<? extends Annotation>[] PARAMETER_KIND_ANNOTATION_CLASSES = Cast.uncheckedNonnullCast(new Class<?>[]{
        ParameterKind.Receiver.class,
        ParameterKind.CallerClassName.class,
        ParameterKind.KotlinDefaultMask.class,
        ParameterKind.VarargParameter.class
    });
}
