/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.instrumentation.processor.codegen.jvmbytecode;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.CallableOwnerInfo;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;
import org.gradle.internal.instrumentation.processor.codegen.HasFailures;
import org.gradle.internal.instrumentation.processor.codegen.JavadocUtils;
import org.gradle.internal.instrumentation.processor.codegen.RequestGroupingInstrumentationClassSourceGenerator;
import org.gradle.internal.instrumentation.util.NameUtil;
import org.objectweb.asm.Type;

import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class JvmInterceptorGenerator extends RequestGroupingInstrumentationClassSourceGenerator {
    /**
     * Emits the code that generates interceptor method invocation.
     */
    @FunctionalInterface
    protected interface InvocationGenerator {
        void generate(CallInterceptionRequest request, FieldSpec implTypeField, CodeBlock.Builder code);
    }

    /**
     * Creates the code block that checks if the invocation operation should be intercepted.
     */
    @FunctionalInterface
    protected interface InvocationMatcher {
        CodeBlock generate(CallableInfo info);
    }

    protected static final FieldSpec METADATA_FIELD =
        FieldSpec.builder(InstrumentationMetadata.class, "metadata", Modifier.PRIVATE, Modifier.FINAL).build();

    protected static final FieldSpec CONTEXT_FIELD =
        FieldSpec.builder(BytecodeInterceptorFilter.class, "context", Modifier.PRIVATE, Modifier.FINAL).build();

    protected final MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE)
        .addParameter(InstrumentationMetadata.class, "metadata")
        .addParameter(BytecodeInterceptorFilter.class, "context")
        .addStatement("this.$N = metadata", InterceptJvmCallsGenerator.METADATA_FIELD)
        .addStatement("this.$N = context", InterceptJvmCallsGenerator.CONTEXT_FIELD)
        .build();

    protected static TypeSpec generateFactoryClass(String className, BytecodeInterceptorType interceptorType) {
        MethodSpec method = MethodSpec.methodBuilder("create")
            .addModifiers(Modifier.PUBLIC)
            .returns(JvmBytecodeCallInterceptor.class)
            .addParameter(InstrumentationMetadata.class, "metadata")
            .addParameter(BytecodeInterceptorFilter.class, "context")
            .addStatement("return new $L($N, $N)", className, "metadata", "context")
            .addAnnotation(Override.class)
            .build();
        return TypeSpec.classBuilder("Factory")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addSuperinterface(ClassName.get(JvmBytecodeCallInterceptor.Factory.class))
            .addSuperinterface(ClassName.get(interceptorType.getInterceptorFactoryMarkerInterface()))
            .addMethod(method)
            .build();
    }

    protected static Map<Type, FieldSpec> generateFieldsForImplementationOwners(Collection<CallInterceptionRequest> interceptionRequests) {
        Set<String> knownSimpleNames = new HashSet<>();
        return interceptionRequests.stream().map(it -> it.getImplementationInfo().getOwner()).distinct()
            .collect(Collectors.toMap(Function.identity(), implementationType -> {
                ClassName implementationClassName = NameUtil.getClassName(implementationType.getClassName());
                String fieldTypeName = knownSimpleNames.add(implementationClassName.simpleName()) ?
                    implementationClassName.simpleName() :
                    implementationClassName.reflectionName();
                String fullFieldName = NameUtil.camelToUpperUnderscoreCase(fieldTypeName) + "_TYPE";
                return FieldSpec.builder(String.class, fullFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$S", implementationClassName.reflectionName().replace(".", "/"))
                    .build();
            }, (u, v) -> u, LinkedHashMap::new));
    }

    protected static void generateCodeForOwner(
        CallableOwnerInfo owner,
        Map<Type, FieldSpec> implTypeFields,
        List<CallInterceptionRequest> requestsForOwner,
        CodeBlock.Builder code,
        InvocationMatcher invocationMatcher,
        InvocationGenerator interceptStandard,
        @Nullable InvocationGenerator interceptKotlinDefault,
        Consumer<? super CallInterceptionRequest> onProcessedRequest,
        Consumer<? super HasFailures.FailureInfo> onFailure
    ) {
        if (owner.isInterceptSubtypes()) {
            code.beginControlFlow("if ($N.isInstanceOf(owner, $S))", METADATA_FIELD, owner.getType().getInternalName());
        } else {
            code.beginControlFlow("if (owner.equals($S))", owner.getType().getInternalName());
        }
        for (CallInterceptionRequest request : requestsForOwner) {
            CodeBlock.Builder nested = CodeBlock.builder();
            try {
                generateCodeForRequest(
                    request,
                    implTypeFields.get(request.getImplementationInfo().getOwner()),
                    nested,
                    invocationMatcher,
                    interceptStandard,
                    interceptKotlinDefault
                );
            } catch (Failure failure) {
                onFailure.accept(new HasFailures.FailureInfo(request, failure.reason));
            }
            onProcessedRequest.accept(request);
            code.add(nested.build());
        }
        code.endControlFlow();
    }

    protected static void generateCodeForRequest(
        CallInterceptionRequest request,
        FieldSpec implTypeField,
        CodeBlock.Builder code,
        InvocationMatcher invocationMatcher,
        InvocationGenerator interceptStandard,
        @Nullable InvocationGenerator interceptKotlinDefault
    ) {
        String callableName = request.getInterceptedCallable().getCallableName();
        CallableInfo interceptedCallable = request.getInterceptedCallable();
        String interceptedCallableDescriptor = standardCallableDescriptor(interceptedCallable);
        validateSignature(interceptedCallable);

        CodeBlock matchInvocationOperation = invocationMatcher.generate(interceptedCallable);

        documentInterceptorGeneratedCode(request, code);
        matchAndInterceptStandardCallableSignature(
            request,
            implTypeField,
            code,
            callableName,
            interceptedCallableDescriptor,
            matchInvocationOperation,
            interceptStandard
        );

        if (interceptKotlinDefault != null && interceptedCallable.hasKotlinDefaultMaskParam()) {
            matchAndInterceptKotlinDefaultSignature(
                request,
                implTypeField,
                code,
                callableName,
                interceptedCallable,
                matchInvocationOperation,
                interceptKotlinDefault
            );
        }
    }

    protected static void matchAndInterceptStandardCallableSignature(
        CallInterceptionRequest request,
        FieldSpec implTypeField,
        CodeBlock.Builder code,
        String callableName,
        String callableDescriptor,
        CodeBlock matchOpcodeExpression,
        InvocationGenerator invocationGenerator
    ) {
        code.beginControlFlow("if (name.equals($S) && descriptor.equals($S) && $L)", callableName, callableDescriptor, matchOpcodeExpression);
        invocationGenerator.generate(request, implTypeField, code);
        code.endControlFlow();
    }

    private static void matchAndInterceptKotlinDefaultSignature(
        CallInterceptionRequest request,
        FieldSpec ownerTypeField,
        CodeBlock.Builder code,
        String callableName,
        CallableInfo interceptedCallable,
        CodeBlock matchOpcodeExpression,
        InvocationGenerator invocationGenerator
    ) {
        code.add("// Additionally intercept the signature with the Kotlin default mask and marker:\n");
        String callableDescriptorKotlinDefault = kotlinDefaultFunctionDescriptor(interceptedCallable);
        String defaultMethodName = callableName + "$default";
        code.beginControlFlow("if (name.equals($S) && descriptor.equals($S) && $L)", defaultMethodName, callableDescriptorKotlinDefault, matchOpcodeExpression);
        invocationGenerator.generate(request, ownerTypeField, code);
        code.endControlFlow();
    }

    private static void documentInterceptorGeneratedCode(CallInterceptionRequest request, CodeBlock.Builder code) {
        code.add("/** \n * Intercepting $L: $L\n", JavadocUtils.callableKindForJavadoc(request), JavadocUtils.interceptedCallableLink(request));
        code.add(" * Intercepted by $L\n*/\n", JavadocUtils.interceptorImplementationLink(request));
    }

    private static void validateSignature(CallableInfo callable) {
        if (callable.getKind() == CallableKindInfo.GROOVY_PROPERTY_GETTER || callable.getKind() == CallableKindInfo.GROOVY_PROPERTY_SETTER) {
            throw new Failure("Groovy property access cannot be intercepted in JVM calls");
        }

        boolean hasInjectVisitorContext = callable.hasInjectVisitorContextParam();
        if (hasInjectVisitorContext) {
            ParameterInfo lastParameter = callable.getParameters().get(callable.getParameters().size() - 1);
            if (lastParameter.getKind() != ParameterKindInfo.INJECT_VISITOR_CONTEXT) {
                throw new Failure("The interceptor's @" + ParameterKind.InjectVisitorContext.class.getSimpleName() + " parameter should be last parameter");
            }
            if (!lastParameter.getParameterType().getClassName().equals(BytecodeInterceptorFilter.class.getName())) {
                throw new Failure("The interceptor's @" + ParameterKind.InjectVisitorContext.class.getSimpleName() + " parameter should be of type " + BytecodeInterceptorFilter.class.getName() + " but was " + lastParameter.getParameterType().getClassName());
            }
            if (callable.getParameters().stream().filter(it -> it.getKind() == ParameterKindInfo.INJECT_VISITOR_CONTEXT).count() > 1) {
                throw new Failure("An interceptor may not have more than one @" + ParameterKind.InjectVisitorContext.class.getSimpleName() + " parameter");
            }
        }

        boolean hasCallerClassName = callable.hasCallerClassNameParam();
        if (hasCallerClassName) {
            int expectedIndex = hasInjectVisitorContext ? callable.getParameters().size() - 2 : callable.getParameters().size() - 1;
            if (callable.getParameters().get(expectedIndex).getKind() != ParameterKindInfo.CALLER_CLASS_NAME) {
                throw new Failure("The interceptor's @" + ParameterKind.CallerClassName.class.getSimpleName() + " parameter should be last or just before @" + ParameterKind.InjectVisitorContext.class.getSimpleName() + " if that parameter is present");
            }
            if (callable.getParameters().stream().filter(it -> it.getKind() == ParameterKindInfo.CALLER_CLASS_NAME).count() > 1) {
                throw new Failure("An interceptor may not have more than one @" + ParameterKind.CallerClassName.class.getSimpleName() + " parameter");
            }
        }

        if (callable.hasKotlinDefaultMaskParam()) {
            // TODO support @AfterConstructor with Kotlin default mask? Kotlin constructors have a special DefaultConstructorMarker as the last argument
            if (callable.getKind() != CallableKindInfo.STATIC_METHOD && callable.getKind() != CallableKindInfo.INSTANCE_METHOD) {
                throw new Failure(
                    "Only @" + CallableKind.StaticMethod.class.getSimpleName() + " or @" + CallableKind.InstanceMethod.class.getSimpleName() + " can use Kotlin default parameters"
                );
            }

            int expectedKotlinDefaultMaskIndex = callable.getParameters().size() - (hasCallerClassName ? 2 : 1);
            if (callable.getParameters().get(expectedKotlinDefaultMaskIndex).getKind() != ParameterKindInfo.KOTLIN_DEFAULT_MASK) {
                throw new Failure(
                    "@" + ParameterKind.KotlinDefaultMask.class.getSimpleName() + " should be the last parameter of may be followed only by @" + ParameterKind.CallerClassName.class.getSimpleName()
                );
            }
        }

        if (callable.getOwner().isInterceptSubtypes() && !callable.getOwner().getType().getInternalName().startsWith("org/gradle")) {
            throw new Failure("Intercepting inherited methods is supported only for Gradle types for now, but type was: " + callable.getOwner().getType().getInternalName());
        }
    }

    private static String standardCallableDescriptor(CallableInfo callableInfo) {
        Type[] parameterTypes = callableInfo.getParameters().stream()
            .filter(it -> it.getKind().isSourceParameter())
            .map(ParameterInfo::getParameterType).toArray(Type[]::new);
        Type returnType = callableInfo.getReturnType().getType();
        return Type.getMethodDescriptor(returnType, parameterTypes);
    }

    private static String kotlinDefaultFunctionDescriptor(CallableInfo callableInfo) {
        if (callableInfo.getKind() != CallableKindInfo.INSTANCE_METHOD && callableInfo.getKind() != CallableKindInfo.STATIC_METHOD) {
            throw new UnsupportedOperationException("Kotlin default parameters are not yet supported for " + callableInfo.getKind());
        }

        String standardDescriptor = standardCallableDescriptor(callableInfo);
        Type returnType = Type.getReturnType(standardDescriptor);
        Type[] argumentTypes = Type.getArgumentTypes(standardDescriptor);
        Type[] argumentTypesWithDefault = Stream.concat(
            Arrays.stream(argumentTypes),
            Stream.of(Type.getType(int.class), Type.getType(Object.class))
        ).toArray(Type[]::new);
        return Type.getMethodDescriptor(returnType, argumentTypesWithDefault);
    }

    protected static class Failure extends RuntimeException {
        public final String reason;

        public Failure(String reason) {
            this.reason = reason;
        }
    }
}
