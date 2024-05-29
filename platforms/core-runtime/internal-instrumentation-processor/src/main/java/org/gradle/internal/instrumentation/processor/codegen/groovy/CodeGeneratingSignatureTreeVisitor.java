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

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;
import org.gradle.internal.instrumentation.processor.codegen.TypeUtils;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.processor.codegen.SignatureUtils.hasCallerClassName;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.PARAMETER;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.RECEIVER;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.RECEIVER_AS_CLASS;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.VARARG;

class CodeGeneratingSignatureTreeVisitor {
    private final Stack<CodeBlock> paramVariablesStack = new Stack<>();
    private final CodeBlock.Builder result;

    CodeGeneratingSignatureTreeVisitor(CodeBlock.Builder result) {
        this.result = result;
    }

    /**
     * @param paramIndex index of the parameter in the signatures, -1 stands for the receiver
     */
    void visit(SignatureTree current, int paramIndex) {
        CallInterceptionRequest leafInCurrent = current.getLeafOrNull();
        if (leafInCurrent != null) {
            generateInvocationWhenArgsMatched(leafInCurrent, paramIndex);
        }
        Map<ParameterMatchEntry, SignatureTree> children = current.getChildrenByMatchEntry();
        if (!children.isEmpty()) {
            boolean hasParamMatchers = children.keySet().stream().anyMatch(it -> it.kind == PARAMETER);
            if (hasParamMatchers) { // is not the receiver or vararg
                result.beginControlFlow("if (invocation.getArgsCount() > $L)", paramIndex);
                result.addStatement("Object arg$1L = invocation.getArgument($1L)", paramIndex);
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
                    generateVarargCheckAndInvocation(entry, child, paramIndex);
                }
            });
        }
    }

    private void generateInvocationWhenArgsMatched(CallInterceptionRequest request, int argCount) {
        result.beginControlFlow("if (invocation.getArgsCount() == $L)", argCount);
        CodeBlock argsCode = prepareInvocationArgs(request);
        emitInvocationCodeWithReturn(request, argsCode);
        result.endControlFlow();
    }

    private CodeBlock prepareInvocationArgs(CallInterceptionRequest request) {
        boolean hasKotlinDefaultMask = request.getInterceptedCallable().getParameters().stream().anyMatch(it -> it.getKind() == ParameterKindInfo.KOTLIN_DEFAULT_MASK);
        boolean hasCallerClassName = hasCallerClassName(request.getInterceptedCallable());
        Stream<CodeBlock> maybeZeroForKotlinDefault = hasKotlinDefaultMask ? Stream.of(CodeBlock.of("0")) : Stream.empty();
        Stream<CodeBlock> maybeCallerClassName = hasCallerClassName ? Stream.of(CodeBlock.of("consumer")) : Stream.empty();
        return Stream.of(
            paramVariablesStack.stream(),
            maybeZeroForKotlinDefault,
            maybeCallerClassName
        ).flatMap(Function.identity()).collect(CodeBlock.joining(", "));
    }

    private void emitInvocationCodeWithReturn(CallInterceptionRequest request, CodeBlock argsCode) {
        TypeName implementationOwner = TypeUtils.typeName(request.getImplementationInfo().getOwner());
        String implementationName = request.getImplementationInfo().getName();
        if (request.getInterceptedCallable().getKind() == CallableKindInfo.AFTER_CONSTRUCTOR) {
            result.addStatement("$1T result = new $1T($2L)", TypeUtils.typeName(request.getInterceptedCallable().getOwner().getType()), paramVariablesStack.stream().collect(CodeBlock.joining(", ")));
            CodeBlock interceptorArgs = CodeBlock.join(Arrays.asList(CodeBlock.of("result"), argsCode), ", ");
            result.addStatement("$T.$L($L)", implementationOwner, implementationName, interceptorArgs);
            result.addStatement("return result");
        } else if (request.getInterceptedCallable().getReturnType().getType().equals(Type.VOID_TYPE)) {
            result.addStatement("$T.$L($L)", implementationOwner, implementationName, argsCode);
            result.addStatement("return null");
        } else {
            result.addStatement("return $T.$L($L)", implementationOwner, implementationName, argsCode);
        }
    }

    private void generateVarargCheckAndInvocation(ParameterMatchEntry entry, SignatureTree child, int paramIndex) {
        TypeName entryParamType = TypeUtils.typeName(entry.type);

        result.add("// Trying to match the vararg invocation\n");
        CodeBlock varargVariable = CodeBlock.of("varargValues");
        result.addStatement("$1T[] $2L = new $1T[invocation.getArgsCount() - $3L]", entryParamType, varargVariable, paramIndex);
        CodeBlock varargMatched = CodeBlock.of("varargMatched");
        result.addStatement("boolean $L = true", varargMatched);
        result.beginControlFlow("for (int argIndex = $1L; argIndex < invocation.getArgsCount(); argIndex++)", paramIndex);

        CodeBlock nextArg = CodeBlock.of("nextArg");
        result.addStatement("Object $L = invocation.getArgument(argIndex)", nextArg);
        result.beginControlFlow("if ($1L == null || $1L instanceof $2T)", nextArg, entryParamType);
        if (entryParamType.equals(TypeName.OBJECT)) {
            result.addStatement("$1L[argIndex - $2L] = $3L", varargVariable, paramIndex, nextArg);
        } else {
            result.addStatement("$1L[argIndex - $2L] = ($3T) $4L", varargVariable, paramIndex, entryParamType, nextArg);
        }
        result.nextControlFlow("else");
        result.addStatement("$L = false", varargMatched);
        result.addStatement("break");
        result.endControlFlow();

        result.endControlFlow();
        result.beginControlFlow("if ($L)", varargMatched);
        paramVariablesStack.push(varargVariable);
        CallInterceptionRequest request = Objects.requireNonNull(child.getLeafOrNull());
        emitInvocationCodeWithReturn(request, prepareInvocationArgs(request));
        paramVariablesStack.pop();
        result.endControlFlow();
    }

    private void generateNormalCallChecksAndVisitSubtree(ParameterMatchEntry entry, SignatureTree child, int paramIndex) {
        CodeBlock argExpr = entry.kind == RECEIVER || entry.kind == RECEIVER_AS_CLASS
            ? CodeBlock.of("receiver")
            : CodeBlock.of("arg$L", paramIndex);

        int childArgCount = paramIndex + 1;
        TypeName entryChildType = TypeUtils.typeName(entry.type);
        CodeBlock matchExpr = entry.kind == RECEIVER_AS_CLASS ?
            CodeBlock.of("$L.equals($T.class)", argExpr, entryChildType) :
            // Vararg fits here, too:
            CodeBlock.of("$1L == null || $1L instanceof $2T", argExpr, entryChildType.box());
        result.beginControlFlow("if ($L)", matchExpr);
        boolean shouldPopParameter = false;
        if (entry.kind != RECEIVER_AS_CLASS) {
            shouldPopParameter = true;
            CodeBlock paramVariable = CodeBlock.of("$LTyped", argExpr);
            if (!entryChildType.equals(TypeName.OBJECT)) {
                result.addStatement("$2T $1L = ($2T) $3L", paramVariable, entryChildType, argExpr);
            } else {
                result.addStatement("$2T $1L = $3L", paramVariable, entryChildType, argExpr);
            }
            paramVariablesStack.push(paramVariable);
        }
        visit(child, childArgCount);
        if (shouldPopParameter) {
            paramVariablesStack.pop();
        }
        result.endControlFlow();
    }
}
