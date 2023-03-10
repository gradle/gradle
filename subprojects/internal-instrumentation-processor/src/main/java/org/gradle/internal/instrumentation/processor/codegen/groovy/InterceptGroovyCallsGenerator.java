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

package org.gradle.internal.instrumentation.processor.codegen.groovy;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGenerator.GenerationResult.HasFailures.FailureInfo;
import org.gradle.internal.instrumentation.processor.codegen.RequestGroupingInstrumentationClassGenerator;
import org.gradle.internal.instrumentation.processor.codegen.TypeUtils;
import org.gradle.util.internal.TextUtil;
import org.objectweb.asm.Type;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.internal.instrumentation.processor.codegen.JavadocUtils.callableKindForJavadoc;
import static org.gradle.internal.instrumentation.processor.codegen.JavadocUtils.interceptedCallableLink;
import static org.gradle.internal.instrumentation.processor.codegen.JavadocUtils.interceptorImplementationLink;

public class InterceptGroovyCallsGenerator extends RequestGroupingInstrumentationClassGenerator {
    @Override
    protected String classNameForRequest(CallInterceptionRequest request) {
        return request.getRequestExtras().getByType(RequestExtra.InterceptGroovyCalls.class)
            .map(RequestExtra.InterceptGroovyCalls::getImplementationClassName)
            .orElse(null);
    }

    @Override
    protected Consumer<TypeSpec.Builder> classContentForClass(
        String className,
        Collection<CallInterceptionRequest> requestsClassGroup,
        Consumer<? super CallInterceptionRequest> onProcessedRequest,
        Consumer<? super FailureInfo> onFailure
    ) {
        List<TypeSpec> interceptorTypeSpecs = generateInterceptorClasses(requestsClassGroup);
        MethodSpec getInterceptors = generateGetInterceptorsMethod(interceptorTypeSpecs);

        return builder -> builder
            .addTypes(interceptorTypeSpecs)
            .addMethod(getInterceptors);
    }

    private static List<TypeSpec> generateInterceptorClasses(Collection<CallInterceptionRequest> interceptionRequests) {
        List<TypeSpec> result = new ArrayList<>(interceptionRequests.size() / 2);

        LinkedHashMap<String, List<CallInterceptionRequest>> namedRequests = new LinkedHashMap<>();
        LinkedHashMap<Type, List<CallInterceptionRequest>> constructorRequests = new LinkedHashMap<>();

        interceptionRequests.forEach(request -> {
            if (request.getRequestExtras().getByType(RequestExtra.InterceptGroovyCalls.class).isPresent()) {
                CallableInfo callable = request.getInterceptedCallable();
                if (callable.getKind() == CallableKindInfo.AFTER_CONSTRUCTOR) {
                    constructorRequests.computeIfAbsent(request.getInterceptedCallable().getOwner(), key -> new ArrayList<>()).add(request);
                } else {
                    String nameKey = callable.getKind() == CallableKindInfo.GROOVY_PROPERTY
                        ? "get" + TextUtil.capitalize(callable.getCallableName())
                        : callable.getCallableName();
                    namedRequests.computeIfAbsent(nameKey, key -> new ArrayList<>()).add(request);
                }
            }
        });

        namedRequests.entrySet().stream()
            .map(it -> generateNamedCallableInterceptorClass(it.getKey(), it.getValue()))
            .collect(Collectors.toCollection(() -> result));

        constructorRequests.entrySet().stream()
            .map(it -> generateConstructorInterceptorClass(it.getKey(), it.getValue()))
            .collect(Collectors.toCollection(() -> result));

        return result;
    }

    private static TypeSpec generateNamedCallableInterceptorClass(String name, List<CallInterceptionRequest> requests) {
        String className = TextUtil.capitalize(name) + "CallInterceptor";
        return generateInterceptorClass(className, namedCallableScopesArgs(name, requests), requests).build();
    }

    private static TypeSpec generateConstructorInterceptorClass(Type constructedType, List<CallInterceptionRequest> requests) {
        String className = ClassName.bestGuess(constructedType.getClassName()).simpleName() + "ConstructorCallInterceptor";
        return generateInterceptorClass(className, constructorScopeArg(TypeUtils.typeName(constructedType)), requests).build();
    }

    private static SignatureTree signatureTreeFromRequests(Collection<CallInterceptionRequest> requests) {
        SignatureTree result = new SignatureTree();
        requests.forEach(result::add);
        return result;
    }

    private static TypeSpec.Builder generateInterceptorClass(String className, CodeBlock scopes, List<CallInterceptionRequest> requests) {
        TypeSpec.Builder generatedClass = TypeSpec.classBuilder(className)
            .superclass(CALL_INTERCEPTOR_CLASS)
            .addJavadoc(interceptorClassJavadoc(requests))
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC);

        MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).addStatement("super($L)", scopes).build();
        generatedClass.addMethod(constructor);

        MethodSpec doIntercept = MethodSpec.methodBuilder("doIntercept")
            .addModifiers(Modifier.PROTECTED)
            .returns(Object.class)
            .addAnnotation(Override.class)
            .addParameter(INVOCATION_CLASS, "invocation")
            .addParameter(String.class, "consumer")
            .addException(Throwable.class)
            .addCode(generateCodeFromInterceptorSignatureTree(signatureTreeFromRequests(requests)))
            .build();

        generatedClass.addMethod(doIntercept);
        return generatedClass;
    }

    private static CodeBlock interceptorClassJavadoc(Collection<CallInterceptionRequest> requests) {
        List<CodeBlock> result = new ArrayList<>();
        result.add(CodeBlock.of("Intercepts the following declarations:<ul>"));
        requests.stream().map(request ->
            CodeBlock.of("<li> $L $L\n     with $L", callableKindForJavadoc(request), interceptedCallableLink(request), interceptorImplementationLink(request))
        ).collect(Collectors.toCollection(() -> result));
        result.add(CodeBlock.of("</ul>"));
        return result.stream().collect(CodeBlock.joining("\n\n"));
    }

    private static CodeBlock constructorScopeArg(TypeName constructedType) {
        return CodeBlock.of("$1T.constructorsOf($2T.class)", INTERCEPTED_SCOPE_CLASS, constructedType);
    }

    private static CodeBlock namedCallableScopesArgs(String name, List<CallInterceptionRequest> requests) {
        List<CodeBlock> scopeExpressions = new ArrayList<>();

        List<CallInterceptionRequest> propertyRequests = requests.stream().filter(it -> it.getInterceptedCallable().getKind() == CallableKindInfo.GROOVY_PROPERTY).collect(Collectors.toList());
        propertyRequests.forEach(request -> {
            String propertyName = request.getInterceptedCallable().getCallableName();
            String getterName = "get" + TextUtil.capitalize(propertyName);
            scopeExpressions.add(CodeBlock.of("$1T.readsOfPropertiesNamed($2S)", INTERCEPTED_SCOPE_CLASS, propertyName));
            scopeExpressions.add(CodeBlock.of("$1T.methodsNamed($2S)", INTERCEPTED_SCOPE_CLASS, getterName));
        });

        List<CallableKindInfo> callableKinds = requests.stream().map(it -> it.getInterceptedCallable().getKind()).distinct().collect(Collectors.toList());
        if (callableKinds.contains(CallableKindInfo.STATIC_METHOD) | callableKinds.contains(CallableKindInfo.INSTANCE_METHOD)) {
            scopeExpressions.add(CodeBlock.of("$T.methodsNamed($S)", INTERCEPTED_SCOPE_CLASS, name));
        }
        return scopeExpressions.stream().distinct().collect(CodeBlock.joining(", "));
    }

    private static CodeBlock generateCodeFromInterceptorSignatureTree(SignatureTree tree) {
        CodeBlock.Builder result = CodeBlock.builder();
        result.addStatement("$T receiver = invocation.getReceiver()", Object.class);

        new CodeGeneratingSignatureTreeVisitor(result).visit(tree, -1);

        result.addStatement("return invocation.callOriginal()");
        return result.build();
    }

    private static final ClassName CALL_INTERCEPTOR_CLASS = ClassName.bestGuess("org.gradle.internal.classpath.intercept.CallInterceptor");
    private static final ClassName INTERCEPTED_SCOPE_CLASS = ClassName.bestGuess("org.gradle.internal.classpath.intercept.InterceptScope");
    private static final ClassName INVOCATION_CLASS = ClassName.bestGuess("org.gradle.internal.classpath.intercept.Invocation");

    private static MethodSpec generateGetInterceptorsMethod(List<TypeSpec> interceptorTypes) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("getCallInterceptors")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), ClassName.bestGuess("org.gradle.internal.classpath.intercept.CallInterceptor")));
        CodeBlock[] constructorCalls = interceptorTypes.stream().map(it -> CodeBlock.builder().add("new $T()", ClassName.bestGuess(it.name)).build()).toArray(CodeBlock[]::new);
        CodeBlock constructorCallsArgs = CodeBlock.builder().add(interceptorTypes.stream().map(it -> "$L").collect(Collectors.joining(",\n")), (Object[]) constructorCalls).build();
        method.addCode("return $T.asList($>\n$L$<\n", Arrays.class, constructorCallsArgs);
        method.addCode(");");
        return method.build();

    }
}
