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

package org.gradle.internal.instrumentation.processor.codegen.jvmbytecode;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind;
import org.gradle.internal.instrumentation.api.jvmbytecode.BridgeMethodBuilder;
import org.gradle.internal.instrumentation.api.jvmbytecode.DefaultBridgeMethodBuilder;
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
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.codegen.HasFailures.FailureInfo;
import org.gradle.internal.instrumentation.processor.codegen.JavadocUtils;
import org.gradle.internal.instrumentation.processor.codegen.RequestGroupingInstrumentationClassSourceGenerator;
import org.gradle.internal.instrumentation.util.NameUtil;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType.GENERATED_ANNOTATION;
import static org.gradle.internal.instrumentation.processor.codegen.TypeUtils.typeName;

/**
 * Generates a single bytecode rewriter class.
 */
public class InterceptJvmCallsGenerator extends RequestGroupingInstrumentationClassSourceGenerator {
    /**
     * Emits the code that generates interceptor method invocation.
     */
    @FunctionalInterface
    private interface InvocationGenerator {
        void generate(CallInterceptionRequest request, FieldSpec implTypeField, CodeBlock.Builder code);
    }

    /**
     * Creates the code block that checks if the invocation operation should be intercepted.
     */
    @FunctionalInterface
    private interface InvocationMatcher {
        CodeBlock generate(CallableInfo info);
    }

    @Override
    protected String classNameForRequest(CallInterceptionRequest request) {
        return request.getRequestExtras().getByType(RequestExtra.InterceptJvmCalls.class)
            .map(RequestExtra.InterceptJvmCalls::getImplementationClassName)
            .orElse(null);
    }

    @Override
    protected Consumer<TypeSpec.Builder> classContentForClass(
        String className,
        List<CallInterceptionRequest> requestsClassGroup,
        Consumer<? super CallInterceptionRequest> onProcessedRequest,
        Consumer<? super FailureInfo> onFailure
    ) {
        Map<Type, FieldSpec> typeFieldByOwner = generateFieldsForImplementationOwners(requestsClassGroup);
        BytecodeInterceptorType interceptorType = requestsClassGroup.get(0).getRequestExtras().getByType(RequestExtra.InterceptJvmCalls.class)
            .map(RequestExtra.InterceptJvmCalls::getInterceptionType)
            .orElseThrow(() -> new IllegalStateException(RequestExtra.InterceptJvmCalls.class.getSimpleName() + " should be present at this stage!"));

        Map<CallableOwnerInfo, List<CallInterceptionRequest>> requestsByOwner = requestsClassGroup.stream().collect(
            Collectors.groupingBy(it -> it.getInterceptedCallable().getOwner(), LinkedHashMap::new, Collectors.toList())
        );

        MethodSpec.Builder visitMethodInsnBuilder = getVisitMethodInsnBuilder();
        generateVisitMethodInsnCode(visitMethodInsnBuilder, typeFieldByOwner, requestsByOwner, onProcessedRequest, onFailure);

        MethodSpec.Builder findBridgeMethodBuilder = getFindBridgeMethodBuilder();
        generateFindBridgeMethodBuilderCode(findBridgeMethodBuilder, typeFieldByOwner, requestsByOwner, onProcessedRequest, onFailure);

        TypeSpec factoryClass = generateFactoryClass(className, interceptorType);

        return builder ->
            builder.addMethod(constructor)
                .addAnnotation(GENERATED_ANNOTATION.asClassName())
                .addModifiers(Modifier.PUBLIC)
                // generic stuff not related to the content:
                .addSuperinterface(ClassName.get(JvmBytecodeCallInterceptor.class))
                .addSuperinterface(ClassName.get(interceptorType.getInterceptorMarkerInterface()))
                .addMethod(BINARY_CLASS_NAME_OF)
                .addMethod(LOAD_BINARY_CLASS_NAME)
                .addField(INTERCEPTORS_REQUEST_TYPE)
                .addField(METADATA_FIELD)
                .addField(CONTEXT_FIELD)
                // actual content:
                .addMethod(visitMethodInsnBuilder.build())
                .addMethod(findBridgeMethodBuilder.build())
                .addFields(typeFieldByOwner.values())
                .addType(factoryClass);
    }

    private static TypeSpec generateFactoryClass(String className, BytecodeInterceptorType interceptorType) {
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


    private static void generateVisitMethodInsnCode(
        MethodSpec.Builder method,
        Map<Type, FieldSpec> typeFieldByOwner,
        Map<CallableOwnerInfo, List<CallInterceptionRequest>> requestsByOwner,
        Consumer<? super CallInterceptionRequest> onProcessedRequest,
        Consumer<? super FailureInfo> onFailure
    ) {
        CodeBlock.Builder code = CodeBlock.builder();
        requestsByOwner.forEach((owner, requests) -> generateCodeForOwner(
            owner,
            typeFieldByOwner,
            requests,
            code,
            InterceptJvmCallsGenerator::matchOpcodeExpression,
            InterceptJvmCallsGenerator::generateInterceptedInvocation,
            InterceptJvmCallsGenerator::generateKotlinDefaultInvocation,
            onProcessedRequest,
            onFailure));
        code.addStatement("return false");
        method.addCode(code.build());
    }

    private static void generateFindBridgeMethodBuilderCode(
        MethodSpec.Builder method,
        Map<Type, FieldSpec> typeFieldByOwner,
        Map<CallableOwnerInfo, List<CallInterceptionRequest>> requestsByOwner,
        Consumer<? super CallInterceptionRequest> onProcessedRequest,
        Consumer<? super FailureInfo> onFailure
    ) {
        CodeBlock.Builder code = CodeBlock.builder();
        requestsByOwner.forEach((owner, requests) -> generateCodeForOwner(
            owner,
            typeFieldByOwner,
            requests,
            code,
            InterceptJvmCallsGenerator::matchTagExpression,
            InterceptJvmCallsGenerator::generateBridgeMethodBuilder,
            null,
            onProcessedRequest,
            onFailure));
        code.addStatement("return null");
        method.addCode(code.build());
    }


    private static void generateBridgeMethodBuilder(CallInterceptionRequest request, FieldSpec implTypeField, CodeBlock.Builder method) {
        String interceptorName = request.getImplementationInfo().getName();
        String interceptorDesc = request.getImplementationInfo().getDescriptor();
        method.addStatement(
            "$1T builder = $1T.create(tag, owner, descriptor, $2N, $3S, $4S)",
            DefaultBridgeMethodBuilder.class,
            implTypeField,
            interceptorName,
            interceptorDesc);
        CallableInfo callable = request.getInterceptedCallable();
        if (callable.hasKotlinDefaultMaskParam()) {
            method.addStatement("builder = builder.withKotlinDefaultMask()");
        }
        if (callable.hasCallerClassNameParam()) {
            method.addStatement("builder = builder.withClassName(className)");
        }
        if (callable.hasInjectVisitorContextParam()) {
            method.addStatement("builder = builder.withVisitorContext(context)");
        }
        method.addStatement("return builder");
    }

    private static CodeBlock matchTagExpression(CallableInfo callableInfo) {
        switch (callableInfo.getKind()) {
            case INSTANCE_METHOD:
                return CodeBlock.of("(tag == $1T.H_INVOKEVIRTUAL || tag == $1T.H_INVOKEINTERFACE)", Opcodes.class);
            case STATIC_METHOD:
                return CodeBlock.of("tag == $T.H_INVOKESTATIC", Opcodes.class);
            case AFTER_CONSTRUCTOR:
                return CodeBlock.of("tag == $T.H_NEWINVOKESPECIAL", Opcodes.class);
            default:
                throw new Failure("Unsupported kind " + callableInfo.getKind());
        }
    }


    private static Map<Type, FieldSpec> generateFieldsForImplementationOwners(Collection<CallInterceptionRequest> interceptionRequests) {
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

    MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE)
        .addParameter(InstrumentationMetadata.class, "metadata")
        .addParameter(BytecodeInterceptorFilter.class, "context")
        .addStatement("this.$N = metadata", METADATA_FIELD)
        .addStatement("this.$N = context", CONTEXT_FIELD)
        .build();

    private static final ParameterSpec METHOD_VISITOR_PARAM = ParameterSpec.builder(MethodVisitorScope.class, "mv").build();

    private static MethodSpec.Builder getVisitMethodInsnBuilder() {
        return MethodSpec.methodBuilder("visitMethodInsn")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(boolean.class)
            .addParameter(METHOD_VISITOR_PARAM)
            .addParameter(String.class, "className")
            .addParameter(int.class, "opcode")
            .addParameter(String.class, "owner")
            .addParameter(String.class, "name")
            .addParameter(String.class, "descriptor")
            .addParameter(boolean.class, "isInterface")
            .addParameter(ParameterizedTypeName.get(Supplier.class, MethodNode.class), "readMethodNode");
    }

    private static MethodSpec.Builder getFindBridgeMethodBuilder() {
        return MethodSpec.methodBuilder("findBridgeMethodBuilder")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(BridgeMethodBuilder.class)
            .addParameter(String.class, "className")
            .addParameter(int.class, "tag")
            .addParameter(String.class, "owner")
            .addParameter(String.class, "name")
            .addParameter(String.class, "descriptor");

    }

    private static final MethodSpec BINARY_CLASS_NAME_OF = MethodSpec.methodBuilder("binaryClassNameOf")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .returns(String.class)
        .addParameter(String.class, "className")
        .addStatement("return $T.getObjectType(className).getClassName()", Type.class)
        .build();

    private static final FieldSpec INTERCEPTORS_REQUEST_TYPE =
        FieldSpec.builder(Type.class, "INTERCEPTORS_REQUEST_TYPE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("$T.getType($T.class)", Type.class, BytecodeInterceptorFilter.class)
            .build();

    private static final MethodSpec LOAD_BINARY_CLASS_NAME = MethodSpec.methodBuilder("loadOwnerBinaryClassName")
        .addModifiers(Modifier.PRIVATE)
        .returns(void.class)
        .addParameter(METHOD_VISITOR_PARAM)
        .addParameter(String.class, "className")
        .addStatement("$1N._LDC($2N(className))", METHOD_VISITOR_PARAM, BINARY_CLASS_NAME_OF)
        .build();

    private static final FieldSpec METADATA_FIELD =
        FieldSpec.builder(InstrumentationMetadata.class, "metadata", Modifier.PRIVATE, Modifier.FINAL).build();

    private static final FieldSpec CONTEXT_FIELD =
        FieldSpec.builder(BytecodeInterceptorFilter.class, "context", Modifier.PRIVATE, Modifier.FINAL).build();

    private static void generateCodeForOwner(
        CallableOwnerInfo owner,
        Map<Type, FieldSpec> implTypeFields,
        List<CallInterceptionRequest> requestsForOwner,
        CodeBlock.Builder code,
        InvocationMatcher invocationMatcher,
        InvocationGenerator interceptStandard,
        @Nullable InvocationGenerator interceptKotlinDefault,
        Consumer<? super CallInterceptionRequest> onProcessedRequest,
        Consumer<? super FailureInfo> onFailure
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
                onFailure.accept(new FailureInfo(request, failure.reason));
            }
            onProcessedRequest.accept(request);
            code.add(nested.build());
        }
        code.endControlFlow();
    }

    private static void generateCodeForRequest(
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

    private static void matchAndInterceptStandardCallableSignature(
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

    private static CodeBlock matchOpcodeExpression(CallableInfo interceptedCallable) {
        switch (interceptedCallable.getKind()) {
            case STATIC_METHOD:
                return CodeBlock.of("opcode == $T.INVOKESTATIC", Opcodes.class);
            case INSTANCE_METHOD:
                return CodeBlock.of("(opcode == $1T.INVOKEVIRTUAL || opcode == $1T.INVOKEINTERFACE)", Opcodes.class);
            case AFTER_CONSTRUCTOR:
                return CodeBlock.of("opcode == $T.INVOKESPECIAL", Opcodes.class);
            default:
                throw new Failure("Could not determine the opcode for intercepting the call");
        }
    }

    // TODO: move validation earlier?

    private static void generateInterceptedInvocation(CallInterceptionRequest request, FieldSpec implTypeField, CodeBlock.Builder method) {
        CallableInfo callable = request.getInterceptedCallable();
        String implementationName = request.getImplementationInfo().getName();
        String implementationDescriptor = request.getImplementationInfo().getDescriptor();

        if (callable.getKind() == CallableKindInfo.STATIC_METHOD || callable.getKind() == CallableKindInfo.INSTANCE_METHOD) {
            generateNormalInterceptedInvocation(implTypeField, callable, implementationName, implementationDescriptor, method);
        } else if (callable.getKind() == CallableKindInfo.AFTER_CONSTRUCTOR) {
            generateInvocationAfterConstructor(implTypeField, method, callable, implementationName, implementationDescriptor);
        }
        method.addStatement("return true");
    }

    private static void generateInvocationAfterConstructor(FieldSpec implOwnerField, CodeBlock.Builder code, CallableInfo callable, String implementationName, String implementationDescriptor) {
        if (callable.getKind() != CallableKindInfo.AFTER_CONSTRUCTOR) {
            throw new IllegalArgumentException("expected after-constructor interceptor");
        }

        List<ParameterInfo> parameters = callable.getParameters();
        if (parameters.get(0).getKind() != ParameterKindInfo.RECEIVER) {
            throw new Failure("Expected @" + ParameterKind.Receiver.class.getSimpleName() + " first parameter in @" + CallableKind.AfterConstructor.class.getSimpleName());
        }
        if (!Type.getReturnType(implementationDescriptor).equals(Type.VOID_TYPE)) {
            throw new Failure("@" + CallableKind.AfterConstructor.class.getSimpleName() + " handlers can only return void");
        }

        CodeBlock maxLocalsVar = CodeBlock.of("maxLocals");
        code.addStatement("int $L = readMethodNode.get().maxLocals", maxLocalsVar);

        // Store the constructor arguments in local variables, so that we can duplicate them for both the constructor and the interceptor:
        Type[] params = Type.getArgumentTypes(standardCallableDescriptor(callable));
        for (int i = params.length - 1; i >= 0; i--) {
            code.addStatement("$1T type$2L = $1T.getType($3T.class)", Type.class, i, typeName(params[i]));
            code.addStatement("int var$1L = $2L + $3L", i, maxLocalsVar, i * 2 /* in case it's long or double */);
            code.addStatement("$1N.visitVarInsn(type$2L.getOpcode($3T.ISTORE), var$2L)", METHOD_VISITOR_PARAM, i, Opcodes.class);
        }
        // Duplicate the receiver without storing it into a local variable, then prepare the arguments for the original invocation:
        code.addStatement("$N._DUP()", METHOD_VISITOR_PARAM);
        for (int i = 0; i < params.length; i++) {
            code.addStatement("$1N.visitVarInsn(type$2L.getOpcode($3T.ILOAD), var$2L)", METHOD_VISITOR_PARAM, i, Opcodes.class);
        }
        // Put the arguments to the stack again, for the "interceptor" invocation:
        code.addStatement("$N._INVOKESPECIAL(owner, name, descriptor)", METHOD_VISITOR_PARAM);
        for (int i = 0; i < params.length; i++) {
            code.addStatement("$1N.visitVarInsn(type$2L.getOpcode($3T.ILOAD), var$2L)", METHOD_VISITOR_PARAM, i, Opcodes.class);
        }
        maybeGenerateLoadBinaryClassNameCall(code, callable);
        maybeGenerateGetStaticInjectVisitorContext(code, callable);
        code.addStatement("$N._INVOKESTATIC($N, $S, $S)", METHOD_VISITOR_PARAM, implOwnerField, implementationName, implementationDescriptor);
    }

    private static void generateNormalInterceptedInvocation(FieldSpec ownerTypeField, CallableInfo callable, String implementationName, String implementationDescriptor, CodeBlock.Builder code) {
        if (callable.getKind() == CallableKindInfo.GROOVY_PROPERTY_GETTER || callable.getKind() == CallableKindInfo.GROOVY_PROPERTY_SETTER) {
            throw new IllegalArgumentException("cannot generate invocation for Groovy property");
        }

        List<ParameterInfo> parameters = callable.getParameters();
        if (parameters.size() > 1 && parameters.get(parameters.size() - 2).getKind() == ParameterKindInfo.KOTLIN_DEFAULT_MASK) {
            // push the default mask equal to zero, meaning that no parameters have the default values
            code.add("// The interceptor expects a Kotlin default mask, add a zero argument:\n");
            code.addStatement("$N._ICONST_0()", METHOD_VISITOR_PARAM);
        }
        maybeGenerateLoadBinaryClassNameCall(code, callable);
        maybeGenerateGetStaticInjectVisitorContext(code, callable);
        code.addStatement("$N._INVOKESTATIC($N, $S, $S)", METHOD_VISITOR_PARAM, ownerTypeField, implementationName, implementationDescriptor);
    }

    private static void generateKotlinDefaultInvocation(CallInterceptionRequest request, FieldSpec ownerTypeField, CodeBlock.Builder method) {
        CallableInfo interceptedCallable = request.getInterceptedCallable();
        if (interceptedCallable.getKind() == CallableKindInfo.GROOVY_PROPERTY_GETTER || interceptedCallable.getKind() == CallableKindInfo.GROOVY_PROPERTY_SETTER) {
            throw new IllegalArgumentException("cannot generate invocation for Groovy property");
        }

        String implementationName = request.getImplementationInfo().getName();
        String implementationDescriptor = request.getImplementationInfo().getDescriptor();

        method.addStatement("$N._POP()", METHOD_VISITOR_PARAM); // pops the default method signature marker
        maybeGenerateLoadBinaryClassNameCall(method, interceptedCallable);
        maybeGenerateGetStaticInjectVisitorContext(method, interceptedCallable);
        method.addStatement("$N._INVOKESTATIC($N, $S, $S)", METHOD_VISITOR_PARAM, ownerTypeField, implementationName, implementationDescriptor);
        method.addStatement("return true");
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

    private static void maybeGenerateLoadBinaryClassNameCall(CodeBlock.Builder code, CallableInfo callableInfo) {
        if (callableInfo.hasCallerClassNameParam()) {
            code.addStatement("$N($N, className)", LOAD_BINARY_CLASS_NAME, METHOD_VISITOR_PARAM);
        }
    }

    private static void maybeGenerateGetStaticInjectVisitorContext(CodeBlock.Builder code, CallableInfo callableInfo) {
        if (callableInfo.hasInjectVisitorContextParam()) {
            code.addStatement("$N._GETSTATIC($N, context.name(), $N.getDescriptor())", METHOD_VISITOR_PARAM, INTERCEPTORS_REQUEST_TYPE, INTERCEPTORS_REQUEST_TYPE);
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

    private static class Failure extends RuntimeException {
        final String reason;

        private Failure(String reason) {
            this.reason = reason;
        }
    }
}
