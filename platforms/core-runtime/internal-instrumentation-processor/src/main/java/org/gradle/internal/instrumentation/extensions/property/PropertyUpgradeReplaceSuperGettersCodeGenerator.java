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

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.gradle.internal.instrumentation.api.jvmbytecode.ReplacementMethodBuilder;
import org.gradle.internal.instrumentation.api.jvmbytecode.SuperReplacementMethodBuilder;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallableOwnerInfo;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.codegen.HasFailures.FailureInfo;
import org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.JvmInterceptorGenerator;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import javax.lang.model.element.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType.GENERATED_ANNOTATION;

/**
 * Generates a single bytecode rewriter class for replacing super-call getters.
 */
public class PropertyUpgradeReplaceSuperGettersCodeGenerator extends JvmInterceptorGenerator {

    @Override
    protected String classNameForRequest(CallInterceptionRequest request) {
        return request.getRequestExtras().getByType(PropertyUpgradeGetterOverrideRequestExtra.class)
            .map(PropertyUpgradeGetterOverrideRequestExtra::getImplementationClassName)
            .orElse(null);
    }

    @Override
    protected Consumer<TypeSpec.Builder> classContentForClass(
        String className,
        List<CallInterceptionRequest> requestsClassGroup,
        Consumer<? super CallInterceptionRequest> onProcessedRequest,
        Consumer<? super FailureInfo> onFailure
    ) {
        // TODO Move much of this to super-type?
        BytecodeInterceptorType interceptorType = requestsClassGroup.get(0).getRequestExtras().getByType(RequestExtra.InterceptJvmCalls.class)
            .map(RequestExtra.InterceptJvmCalls::getInterceptionType)
            .orElseThrow(() -> new IllegalStateException(RequestExtra.InterceptJvmCalls.class.getSimpleName() + " should be present at this stage!"));

        Map<Type, FieldSpec> typeFieldByOwner = generateFieldsForImplementationOwners(requestsClassGroup);

        Map<CallableOwnerInfo, List<CallInterceptionRequest>> requestsByOwner = requestsClassGroup.stream().collect(
            Collectors.groupingBy(it -> it.getInterceptedCallable().getOwner(), LinkedHashMap::new, Collectors.toList())
        );

        MethodSpec.Builder visitReplacementMethodBuilder = getVisitReplacementMethodBuilder();
        generateVisitReplacementMethodCode(visitReplacementMethodBuilder, typeFieldByOwner, requestsByOwner, onProcessedRequest, onFailure);

        TypeSpec factoryClass = generateFactoryClass(className, interceptorType);

        return builder ->
            builder.addMethod(constructor)
                .addAnnotation(GENERATED_ANNOTATION.asClassName())
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(JvmBytecodeCallInterceptor.class))
                .addSuperinterface(ClassName.get(interceptorType.getInterceptorMarkerInterface()))
                .addField(METADATA_FIELD)
                .addField(CONTEXT_FIELD)
                .addMethod(visitReplacementMethodBuilder.build())
                .addType(factoryClass);
    }

    @SuppressWarnings("unused")
    private static void generateVisitReplacementMethodCode(
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
            // TODO Avoid adding '&& true'
            callable -> CodeBlock.of("true"),
            PropertyUpgradeReplaceSuperGettersCodeGenerator::generateReplacementMethod,
            null,
            onProcessedRequest,
            onFailure));
        code.addStatement("return null");
        method.addCode(code.build());
    }

    private static MethodSpec.Builder getVisitReplacementMethodBuilder() {
        return MethodSpec.methodBuilder("findReplacementMethod")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ReplacementMethodBuilder.class)
            .addParameter(String.class, "owner")
            .addParameter(int.class, "access")
            .addParameter(String.class, "name")
            .addParameter(String.class, "descriptor")
            .addParameter(String.class, "signature")
            .addParameter(ArrayTypeName.of(String.class), "exceptions")
            .addParameter(ParameterizedTypeName.get(Supplier.class, MethodNode.class), "readMethodNode");
    }

    private static void generateReplacementMethod(CallInterceptionRequest request, FieldSpec ownerTypeField, CodeBlock.Builder method) {
        PropertyUpgradeGetterOverrideRequestExtra extra = request.getRequestExtras().getByType(PropertyUpgradeGetterOverrideRequestExtra.class)
            .orElseThrow(() -> new IllegalStateException("PropertyUpgradeGetterOverrideRequestExtra should be present at this stage!"));
        method.addStatement(
            "return new $1T(owner, name, \"$2N\")",
            SuperReplacementMethodBuilder.class,
            extra.getMethodDescriptor()
        );
    }
}
