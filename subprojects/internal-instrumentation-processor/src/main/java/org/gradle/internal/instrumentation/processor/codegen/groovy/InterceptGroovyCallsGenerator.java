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
import org.gradle.internal.instrumentation.model.ParameterKindInfo;
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
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.processor.codegen.SignatureUtils.hasCallerClassName;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.PARAMETER;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.RECEIVER;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.RECEIVER_AS_CLASS;

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
                    namedRequests.computeIfAbsent(callable.getCallableName(), key -> new ArrayList<>()).add(request);
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
        SignatureTree signatureTree = signatureTreeFromRequests(requests);
        return generateInterceptorClass(TextUtil.capitalize(name) + "CallInterceptor", namedCallableScopesArgs(name, requests), signatureTree).build();
    }

    private static TypeSpec generateConstructorInterceptorClass(Type constructedType, List<CallInterceptionRequest> requests) {
        SignatureTree signatureTree = signatureTreeFromRequests(requests);
        TypeSpec.Builder generatedClass = generateInterceptorClass(
            ClassName.bestGuess(constructedType.getClassName()).simpleName() + "ConstructorCallInterceptor",
            constructorScopeArg(TypeUtils.typeName(constructedType)),
            signatureTree
        );

        return generatedClass.build();
    }

    private static SignatureTree signatureTreeFromRequests(Collection<CallInterceptionRequest> requests) {
        SignatureTree result = new SignatureTree();
        requests.forEach(result::add);
        return result;
    }

    private static TypeSpec.Builder generateInterceptorClass(String className, CodeBlock scopes, SignatureTree signatureTree) {
        TypeSpec.Builder generatedClass = TypeSpec.classBuilder(className)
            .superclass(CALL_INTERCEPTOR_CLASS)
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
            .addCode(generateCodeFromInterceptorSignatureTree(signatureTree))
            .build();

        generatedClass.addMethod(doIntercept);
        return generatedClass;
    }

    private static CodeBlock constructorScopeArg(TypeName constructedType) {
        return CodeBlock.of("$1T.constructorsOf($2T.class)", INTERCEPTED_SCOPE_CLASS, constructedType);
    }

    private static CodeBlock namedCallableScopesArgs(String name, List<CallInterceptionRequest> requests) {
        List<CallableKindInfo> callableKinds = requests.stream().map(it -> it.getInterceptedCallable().getKind()).distinct().collect(Collectors.toList());
        CodeBlock.Builder scopes = CodeBlock.builder();
        boolean isFirstScope = true;
        if (callableKinds.contains(CallableKindInfo.GROOVY_PROPERTY)) {
            scopes.add("$1T.readsOfPropertiesNamed($2S), $1T.methodsNamed($3S)", CALL_INTERCEPTOR_CLASS, name, "get" + TextUtil.capitalize(name));
            isFirstScope = false;
        }
        if (callableKinds.contains(CallableKindInfo.STATIC_METHOD) | callableKinds.contains(CallableKindInfo.INSTANCE_METHOD)) {
            if (!isFirstScope) {
                scopes.add(", ");
            }
            scopes.add(CodeBlock.of("$T.methodsNamed($S)", INTERCEPTED_SCOPE_CLASS, name));
        }
        return scopes.build();
    }

    private static CodeBlock generateCodeFromInterceptorSignatureTree(SignatureTree tree) {
        CodeBlock.Builder result = CodeBlock.builder();
        result.addStatement("$T receiver = invocation.getReceiver()", Object.class);

        class CodeGeneratingSignatureTreeVisitor {
            private final Stack<CodeBlock> paramVariablesStack = new Stack<>();

            /**
             * @param paramIndex index of the parameter in the signatures, -1 stands for the receiver
             */
            void visit(SignatureTree current, int paramIndex) {
                CallInterceptionRequest leafInCurrent = current.getLeafOrNull();
                if (leafInCurrent != null) {
                    generateInvocation(leafInCurrent, paramIndex);
                }
                Map<ParameterMatchEntry, SignatureTree> children = current.getChildrenByMatchEntry();
                if (!children.isEmpty()) {
                    boolean hasParamMatchers = children.keySet().stream().anyMatch(it -> it.kind == PARAMETER);
                    if (hasParamMatchers) { // is not the receiver
                        result.addStatement("Object arg$1L = invocation.getArgument($1L)", paramIndex);
                    }
                    children.forEach((entry, child) -> generateChecksAndVisitSubtree(paramIndex, entry, child, paramIndex));
                }
            }

            private void generateInvocation(CallInterceptionRequest request, int argCount) {
                TypeName implementationOwner = TypeUtils.typeName(request.getImplementationInfo().getOwner());
                result.beginControlFlow("if (invocation.getArgsCount() == $L)", argCount);

                boolean hasKotlinDefaultMask = request.getInterceptedCallable().getParameters().stream().anyMatch(it -> it.getKind() == ParameterKindInfo.KOTLIN_DEFAULT_MASK);
                boolean hasCallerClassName = hasCallerClassName(request.getInterceptedCallable());
                Stream<CodeBlock> maybeZeroForKotlinDefault = hasKotlinDefaultMask ? Stream.of(CodeBlock.of("0")) : Stream.empty();
                Stream<CodeBlock> maybeCallerClassName = hasCallerClassName ? Stream.of(CodeBlock.of("consumer")) : Stream.empty();
                CodeBlock argsCode = Stream.of(paramVariablesStack.stream(), maybeZeroForKotlinDefault, maybeCallerClassName).flatMap(Function.identity()).collect(CodeBlock.joining(", "));

                if (request.getInterceptedCallable().getKind() == CallableKindInfo.AFTER_CONSTRUCTOR) {
                    result.addStatement("$1T result = new $1T($2L)", TypeUtils.typeName(request.getInterceptedCallable().getOwner()), paramVariablesStack.stream().collect(CodeBlock.joining(", ")));
                    CodeBlock interceptorArgs = CodeBlock.join(Arrays.asList(CodeBlock.of("result"), argsCode), ", ");
                    result.addStatement("$T.$L($L)", implementationOwner, request.getImplementationInfo().getName(), interceptorArgs);
                    result.addStatement("return result");
                } else if (request.getInterceptedCallable().getReturnType() == Type.VOID_TYPE) {
                    result.addStatement("$T.$L($L)", implementationOwner, request.getImplementationInfo().getName(), argsCode);
                    result.addStatement("return null");
                } else {
                    result.addStatement("return $T.$L($L)", implementationOwner, request.getImplementationInfo().getName(), argsCode);
                }
                result.endControlFlow();
            }

            private void generateChecksAndVisitSubtree(int argCount, ParameterMatchEntry entry, SignatureTree child, int paramIndex) {
                CodeBlock argExpr = entry.kind == RECEIVER || entry.kind == RECEIVER_AS_CLASS
                    ? CodeBlock.of("receiver")
                    : CodeBlock.of("arg$L", paramIndex);

                int childArgCount = argCount + 1;
                TypeName entryChildType = TypeUtils.typeName(entry.type);
                CodeBlock matchExpr = entry.kind == RECEIVER_AS_CLASS ?
                    CodeBlock.of("$L.equals($T.class)", argExpr, entryChildType) :
                    CodeBlock.of("$L instanceof $T", argExpr, entryChildType.box());
                result.beginControlFlow("if ($L)", matchExpr);
                boolean shouldPopParameter = false;
                if (entry.kind != RECEIVER_AS_CLASS) {
                    shouldPopParameter = true;
                    CodeBlock paramVariable = CodeBlock.of("$LTyped", argExpr);
                    result.addStatement("$2T $1L = ($2T) $3L", paramVariable, entryChildType, argExpr);
                    paramVariablesStack.push(paramVariable);
                }
                visit(child, childArgCount);
                if (shouldPopParameter) {
                    paramVariablesStack.pop();
                }
                result.endControlFlow();
            }
        }
        new CodeGeneratingSignatureTreeVisitor().visit(tree, -1);

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
