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
import com.squareup.javapoet.TypeName;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.processor.codegen.JavadocUtils;
import org.gradle.internal.instrumentation.processor.codegen.TypeUtils;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.Objects;

import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.PARAMETER;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.RECEIVER;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.RECEIVER_AS_CLASS;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.VARARG;

class MatchesSignatureGeneratingSignatureTreeVisitor {
    private final CodeBlock.Builder result;

    private static final TypeName SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH =
        ClassName.bestGuess("org.gradle.internal.classpath.intercept.SignatureAwareCallInterceptor.SignatureMatch");

    MatchesSignatureGeneratingSignatureTreeVisitor(CodeBlock.Builder result) {
        this.result = result;
    }

    /**
     * @param paramIndex index of the parameter in the signatures, -1 stands for the receiver
     */
    void visit(SignatureTree current, int paramIndex) {
        CallInterceptionRequest leafInCurrent = current.getLeafOrNull();
        if (leafInCurrent != null) {
            returnTrueIfNoArgumentsLeft(paramIndex, leafInCurrent);
        }
        Map<ParameterMatchEntry, SignatureTree> children = current.getChildrenByMatchEntry();
        if (!children.isEmpty()) {
            boolean hasParamMatchers = children.keySet().stream().anyMatch(it -> it.kind == PARAMETER);
            if (hasParamMatchers) { // is not the receiver or vararg
                result.beginControlFlow("if (argumentClasses.length > $L)", paramIndex);
                result.addStatement("Class<?> arg$1L = argumentClasses[$1L]", paramIndex);
            }
            // Visit non-vararg invocations first and varargs after:
            children.forEach((entry, child) -> {
                if (entry.kind != VARARG) {
                    generateNormalCallChecksAndVisitSubtree(entry, child, paramIndex);
                }
            });
            if (hasParamMatchers) {
                result.endControlFlow();
            }
            children.forEach((entry, child) -> {
                if (entry.kind == VARARG) {
                    generateVarargCheck(entry, child, paramIndex);
                }
            });
        }
    }

    private void generateNormalCallChecksAndVisitSubtree(ParameterMatchEntry entry, SignatureTree child, int paramIndex) {
        CodeBlock argExpr = entry.kind == RECEIVER || entry.kind == RECEIVER_AS_CLASS
            ? CodeBlock.of("receiverClass")
            : CodeBlock.of("arg$L", paramIndex);

        int childArgCount = paramIndex + 1;
        TypeName entryChildType = TypeUtils.typeName(entry.type);
        CodeBlock matchExpr = entry.kind == RECEIVER_AS_CLASS ?
            CodeBlock.of("isStatic && $T.class.isAssignableFrom($L)", entryChildType.box(), argExpr) :
            entry.kind == RECEIVER ?
                CodeBlock.of("!isStatic && ($2L == null || $1T.class.isAssignableFrom($2L))", entryChildType.box(), argExpr) :
                CodeBlock.of("$2L == null || $1T.class.isAssignableFrom($2L)", entryChildType.box(), argExpr);
        // Vararg fits here, too:
        result.beginControlFlow("if ($L)", matchExpr);
        visit(child, childArgCount);
        result.endControlFlow();
    }

    private void generateVarargCheck(ParameterMatchEntry entry, SignatureTree child, int paramIndex) {
        TypeName entryParamType = TypeUtils.typeName(entry.type);
        CallInterceptionRequest childRequest = child.getLeafOrNull();
        Objects.requireNonNull(childRequest, "vararg parameter must be the last in the signature");

        result.add("// Trying to match the vararg invocation\n");
        CodeBlock varargMatched = CodeBlock.of("varargMatched");

        CodeBlock matchArgs = argClassesExpression(childRequest);

        result.beginControlFlow("if (argumentClasses.length == $1L && argumentClasses[$2L] != null && $3T[].class.isAssignableFrom($4T.newInstance(argumentClasses[$2L], 0).getClass()))",
            paramIndex + 1, paramIndex, entryParamType, Array.class
        );
        result.add("/** Matched $L */\n", JavadocUtils.interceptedCallableLink(childRequest));
        result.addStatement("return new $T(true, $L)", SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH, matchArgs);
        result.endControlFlow();

        result.addStatement("boolean $L = true", varargMatched);
        result.beginControlFlow("for (int argIndex = $1L; argIndex < argumentClasses.length; argIndex++)", paramIndex);
        CodeBlock nextArg = CodeBlock.of("nextArg");
        result.addStatement("Class<?> $L = argumentClasses[argIndex]", nextArg);
        result.beginControlFlow("if ($2L != null && !$1T.class.isAssignableFrom($2L))", entryParamType, nextArg);
        result.addStatement("$L = false", varargMatched);
        result.addStatement("break");
        result.endControlFlow();
        result.endControlFlow();
        result.beginControlFlow("if ($L)", varargMatched);
        result.add("/** Matched $L */\n", JavadocUtils.interceptedCallableLink(childRequest));
        result.addStatement("return new $T(true, $L)", SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH, matchArgs);
        result.endControlFlow();
    }

    private void returnTrueIfNoArgumentsLeft(int argCount, CallInterceptionRequest leafInCurrent) {
        CodeBlock argClasses = argClassesExpression(leafInCurrent);

        result.beginControlFlow("if (argumentClasses.length == $L)", argCount);
        result.add("/** Matched $L */\n", JavadocUtils.interceptedCallableLink(leafInCurrent));
        result.addStatement("return new $T(false, $L)", SIGNATURE_AWARE_CALL_INTERCEPTOR_SIGNATURE_MATCH, argClasses);
        result.endControlFlow();
    }

    private static CodeBlock argClassesExpression(CallInterceptionRequest leafInCurrent) {
        return leafInCurrent.getInterceptedCallable().getParameters()
            .stream()
            .filter(it -> it.getKind().isSourceParameter())
            .map(it -> CodeBlock.of("$T.class", TypeUtils.typeName(it.getParameterType())))
            .collect(CodeBlock.joining(", ", "new Class<?>[] {", "}"));
    }


}
