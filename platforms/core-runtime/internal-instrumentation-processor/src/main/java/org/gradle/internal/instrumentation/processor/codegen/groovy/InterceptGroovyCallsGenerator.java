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

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.codegen.HasFailures;
import org.gradle.internal.instrumentation.processor.codegen.RequestGroupingInstrumentationClassSourceGenerator;
import org.gradle.internal.instrumentation.processor.codegen.SignatureUtils;
import org.gradle.internal.instrumentation.processor.codegen.TypeUtils;
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.ConstructorInterceptorSpec;
import org.gradle.internal.instrumentation.processor.codegen.groovy.CallInterceptorSpecs.CallInterceptorSpec.NamedCallableInterceptorSpec;
import org.gradle.internal.instrumentation.util.NameUtil;
import org.gradle.util.internal.TextUtil;
import org.objectweb.asm.Type;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.internal.instrumentation.model.CallableKindInfo.GROOVY_PROPERTY_GETTER;
import static org.gradle.internal.instrumentation.model.CallableKindInfo.GROOVY_PROPERTY_SETTER;
import static org.gradle.internal.instrumentation.processor.codegen.JavadocUtils.callableKindForJavadoc;
import static org.gradle.internal.instrumentation.processor.codegen.JavadocUtils.interceptedCallableLink;
import static org.gradle.internal.instrumentation.processor.codegen.JavadocUtils.interceptorImplementationLink;

public class InterceptGroovyCallsGenerator extends RequestGroupingInstrumentationClassSourceGenerator {
    @Override
    protected String classNameForRequest(CallInterceptionRequest request) {
        return request.getRequestExtras().getByType(RequestExtra.InterceptGroovyCalls.class)
            .map(RequestExtra.InterceptGroovyCalls::getImplementationClassName)
            .orElse(null);
    }

    @Override
    protected Consumer<TypeSpec.Builder> classContentForClass(
        String className,
        List<CallInterceptionRequest> requestsClassGroup,
        Consumer<? super CallInterceptionRequest> onProcessedRequest,
        Consumer<? super HasFailures.FailureInfo> onFailure
    ) {
        List<TypeSpec> interceptorTypeSpecs = generateInterceptorClasses(requestsClassGroup, onFailure);

        return builder -> builder
            .addModifiers(Modifier.PUBLIC)
            .addTypes(interceptorTypeSpecs);
    }

    private static List<TypeSpec> generateInterceptorClasses(Collection<CallInterceptionRequest> interceptionRequests, Consumer<? super HasFailures.FailureInfo> onFailure) {
        List<TypeSpec> result = new ArrayList<>(interceptionRequests.size() / 2);

        CallInterceptorSpecs callInterceptorSpecs = GroovyClassGeneratorUtils.groupRequests(interceptionRequests);
        callInterceptorSpecs.getNamedRequests().stream()
            .peek(spec -> validateRequests(spec.getRequests(), onFailure))
            .map(InterceptGroovyCallsGenerator::generateNamedCallableInterceptorClass)
            .collect(Collectors.toCollection(() -> result));

        callInterceptorSpecs.getConstructorRequests().stream()
            .peek(spec -> validateRequests(spec.getRequests(), onFailure))
            .map(InterceptGroovyCallsGenerator::generateConstructorInterceptorClass)
            .collect(Collectors.toCollection(() -> result));

        return result;
    }

    private static TypeSpec generateNamedCallableInterceptorClass(NamedCallableInterceptorSpec spec) {
        return generateInterceptorClass(spec.getClassName(), spec.getInterceptorType(), namedCallableScopesArgs(spec.getName(), spec.getRequests()), spec.getRequests()).build();
    }

    private static TypeSpec generateConstructorInterceptorClass(ConstructorInterceptorSpec spec) {
        return generateInterceptorClass(spec.getClassName(), spec.getInterceptorType(), constructorScopeArg(TypeUtils.typeName(spec.getConstructorType())), spec.getRequests()).build();
    }

    private static SignatureTree signatureTreeFromRequests(Collection<CallInterceptionRequest> requests) {
        SignatureTree result = new SignatureTree();
        requests.forEach(result::add);
        return result;
    }

    private static TypeSpec.Builder generateInterceptorClass(String className, BytecodeInterceptorType interceptorType, CodeBlock scopes, List<CallInterceptionRequest> requests) {
        TypeSpec.Builder generatedClass = TypeSpec.classBuilder(className)
            .superclass(CALL_INTERCEPTOR_CLASS)
            .addSuperinterface(SIGNATURE_AWARE_CALL_INTERCEPTOR_CLASS)
            .addSuperinterface(interceptorType.getInterceptorMarkerInterface())
            .addJavadoc(interceptorClassJavadoc(requests))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).addStatement("super($L)", scopes).build();
        generatedClass.addMethod(constructor);

        SignatureTree signatureTree = signatureTreeFromRequests(requests);

        MethodSpec doIntercept = MethodSpec.methodBuilder("doIntercept")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(Object.class)
            .addParameter(INVOCATION_CLASS, "invocation")
            .addParameter(String.class, "consumer")
            .addException(Throwable.class)
            .addCode(generateCodeFromInterceptorSignatureTree(signatureTree))
            .build();

        ParameterizedTypeName classWildcard = ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class));
        MethodSpec matchesSignature = MethodSpec.methodBuilder("matchesMethodSignature")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH)
            .addParameter(classWildcard, "receiverClass")
            .addParameter(ArrayTypeName.of(classWildcard), "argumentClasses")
            .addParameter(boolean.class, "isStatic")
            .addCode(generateMatchesSignatureCodeFromInterceptorSignatureTree(signatureTree))
            .build();

        generatedClass.addMethod(doIntercept);
        generatedClass.addMethod(matchesSignature);

        if (hasGroovyPropertyRequests(requests)) {
            generatedClass.addSuperinterface(PROPERTY_AWARE_CALL_INTERCEPTOR_CLASS);
            MethodSpec matchesProperty = MethodSpec.methodBuilder("matchesProperty")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(classWildcard)
                .addParameter(classWildcard, "receiverClass")
                .addCode(generateMatchesPropertyCode(requests))
                .build();
            generatedClass.addMethod(matchesProperty);
        }

        return generatedClass;
    }

    private static void validateRequests(List<CallInterceptionRequest> requests, Consumer<? super HasFailures.FailureInfo> onFailure) {
        for (CallInterceptionRequest request : requests) {
            if (SignatureUtils.hasInjectVisitorContext(request.getInterceptedCallable())) {
                onFailure.accept(new HasFailures.FailureInfo(request, "Parameter with @InjectVisitorContext annotation is not supported for Groovy interception."));
            }
        }
    }

    private static boolean hasGroovyPropertyRequests(List<CallInterceptionRequest> requests) {
        return requests.stream().anyMatch(it -> it.getInterceptedCallable().getKind() == GROOVY_PROPERTY_GETTER || it.getInterceptedCallable().getKind() == GROOVY_PROPERTY_SETTER);
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

        requests.stream().filter(it -> it.getInterceptedCallable().getKind() == GROOVY_PROPERTY_GETTER).forEach(request -> {
            String propertyName = request.getInterceptedCallable().getCallableName();
            String getterName = NameUtil.getterName(request.getInterceptedCallable().getCallableName(), request.getInterceptedCallable().getReturnType().getType());
            scopeExpressions.add(CodeBlock.of("$1T.readsOfPropertiesNamed($2S)", INTERCEPTED_SCOPE_CLASS, propertyName));
            scopeExpressions.add(CodeBlock.of("$1T.methodsNamed($2S)", INTERCEPTED_SCOPE_CLASS, getterName));
        });
        requests.stream().filter(it -> it.getInterceptedCallable().getKind() == CallableKindInfo.GROOVY_PROPERTY_SETTER).forEach(request -> {
            String propertyName = request.getInterceptedCallable().getCallableName();
            String setterName = "set" + TextUtil.capitalize(propertyName);
            scopeExpressions.add(CodeBlock.of("$1T.writesOfPropertiesNamed($2S)", INTERCEPTED_SCOPE_CLASS, propertyName));
            scopeExpressions.add(CodeBlock.of("$1T.methodsNamed($2S)", INTERCEPTED_SCOPE_CLASS, setterName));
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

    private static CodeBlock generateMatchesSignatureCodeFromInterceptorSignatureTree(SignatureTree tree) {
        CodeBlock.Builder result = CodeBlock.builder();
        new MatchesSignatureGeneratingSignatureTreeVisitor(result).visit(tree, -1);
        result.addStatement("return null");
        return result.build();
    }

    private static CodeBlock generateMatchesPropertyCode(Collection<CallInterceptionRequest> requests) {
        CodeBlock.Builder result = CodeBlock.builder();
        LinkedHashMap<Type, Type> propertyTypeByReceiverType = requests.stream()
            .filter(request -> request.getInterceptedCallable().getKind() == GROOVY_PROPERTY_GETTER || request.getInterceptedCallable().getKind() == CallableKindInfo.GROOVY_PROPERTY_SETTER)
            .collect(
                Collectors.toMap(
                    InterceptGroovyCallsGenerator::propertyReceiverType,
                    InterceptGroovyCallsGenerator::propertyValueType,
                    (a, b) -> {
                        if (!a.equals(b)) {
                            throw new IllegalArgumentException("multiple requests to intercept a property on a single receiver type " +
                                "with different property types: " + a + ", " + b);
                        } // otherwise, it's OK, we recognize them in the same way
                        return a;
                    },
                    LinkedHashMap::new
                )
            );
        propertyTypeByReceiverType.forEach((receiverType, propertyType) -> {
            result.beginControlFlow("if ($T.class.isAssignableFrom(receiverClass))", TypeUtils.typeName(receiverType).box());
            result.addStatement("return $T.class", TypeUtils.typeName(propertyType).box());
            result.endControlFlow();
        });
        result.addStatement("return null");
        return result.build();
    }

    private static Type propertyValueType(CallInterceptionRequest request) {
        if (request.getInterceptedCallable().getKind() == GROOVY_PROPERTY_GETTER) {
            return request.getInterceptedCallable().getReturnType().getType();
        } else if (request.getInterceptedCallable().getKind() == CallableKindInfo.GROOVY_PROPERTY_SETTER) {
            Optional<ParameterInfo> newValueParameter =
                request.getInterceptedCallable().getParameters().stream().filter(parameter -> parameter.getKind() == ParameterKindInfo.METHOD_PARAMETER).findFirst();
            return newValueParameter.orElseThrow(() -> new IllegalArgumentException("a setter interceptor must accept a parameter")).getParameterType();
        } else {
            throw new IllegalArgumentException("expected a property interception request, got " + request);
        }
    }

    private static Type propertyReceiverType(CallInterceptionRequest request) {
        return request.getInterceptedCallable().getParameters().stream().filter(it -> it.getKind() == ParameterKindInfo.RECEIVER).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("a property interception request must have a receiver parameter")).getParameterType();
    }

    static final ClassName CALL_INTERCEPTOR_CLASS = ClassName.bestGuess("org.gradle.internal.classpath.intercept.CallInterceptor");
    private static final ClassName SIGNATURE_AWARE_CALL_INTERCEPTOR_CLASS = ClassName.bestGuess("org.gradle.internal.classpath.intercept.SignatureAwareCallInterceptor");
    private static final ClassName SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH =
        ClassName.bestGuess("org.gradle.internal.classpath.intercept.SignatureAwareCallInterceptor.SignatureMatch");
    private static final ClassName PROPERTY_AWARE_CALL_INTERCEPTOR_CLASS = ClassName.bestGuess("org.gradle.internal.classpath.intercept.PropertyAwareCallInterceptor");
    private static final ClassName INTERCEPTED_SCOPE_CLASS = ClassName.bestGuess("org.gradle.internal.classpath.intercept.InterceptScope");
    private static final ClassName INVOCATION_CLASS = ClassName.bestGuess("org.gradle.internal.classpath.intercept.Invocation");
}
