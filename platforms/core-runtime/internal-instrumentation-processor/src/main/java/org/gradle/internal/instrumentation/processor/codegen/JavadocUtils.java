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

package org.gradle.internal.instrumentation.processor.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;

import java.util.List;
import java.util.stream.Collectors;

import static org.gradle.internal.instrumentation.processor.codegen.TypeUtils.typeName;

public class JavadocUtils {

    public static String callableKindForJavadoc(CallInterceptionRequest request) {
        CallableInfo interceptedCallable = request.getInterceptedCallable();
        CallableKindInfo kind = interceptedCallable.getKind();
        return kind == CallableKindInfo.STATIC_METHOD ? "static method" :
            kind == CallableKindInfo.INSTANCE_METHOD ? "instance method" :
                kind == CallableKindInfo.AFTER_CONSTRUCTOR ? "constructor (getting notified after it)" :
                    kind == CallableKindInfo.GROOVY_PROPERTY_GETTER ? "Groovy property getter" :
                        kind == CallableKindInfo.GROOVY_PROPERTY_SETTER ? "Groovy property setter" : null;
    }

    public static CodeBlock interceptedCallableLink(CallInterceptionRequest request) {
        CodeBlock.Builder result = CodeBlock.builder();
        CallableInfo interceptedCallable = request.getInterceptedCallable();
        ClassName className = ClassName.bestGuess(interceptedCallable.getOwner().getType().getClassName());
        String callableNameForDocComment = interceptedCallable.getKind() == CallableKindInfo.AFTER_CONSTRUCTOR ? className.simpleName() : interceptedCallable.getCallableName();
        List<ParameterInfo> params = request.getInterceptedCallable().getParameters();
        List<ParameterInfo> methodParameters = params.stream().filter(parameter -> parameter.getKind().isSourceParameter()).collect(Collectors.toList());
        result.add("{@link $L#$L", className, callableNameForDocComment);
        if (interceptedCallable.getKind() != CallableKindInfo.GROOVY_PROPERTY_GETTER && interceptedCallable.getKind() != CallableKindInfo.GROOVY_PROPERTY_SETTER) {
            result.add("(");
            methodParameters.forEach(parameter -> {
                result.add("$L", parameterTypeForJavadoc(parameter, true));
                if (parameter != methodParameters.get(methodParameters.size() - 1)) {
                    result.add(", ");
                }
            });
            result.add(")");
        }
        result.add("}");
        return result.build();
    }

    public static CodeBlock interceptorImplementationLink(CallInterceptionRequest request) {
        CodeBlock.Builder result = CodeBlock.builder();
        List<ParameterInfo> params = request.getInterceptedCallable().getParameters();
        result.add("{@link $T#$L(", ClassName.bestGuess(request.getImplementationInfo().getOwner().getClassName()), request.getImplementationInfo().getName());
        params.forEach(parameter -> {
            result.add("$L", parameterTypeForJavadoc(parameter, false));
            if (parameter != params.get(params.size() - 1)) {
                result.add(", ");
            }
        });
        result.add(")}");
        return result.build();
    }

    private static CodeBlock parameterTypeForJavadoc(ParameterInfo parameterInfo, boolean renderVararg) {
        if (parameterInfo.getKind() == ParameterKindInfo.VARARG_METHOD_PARAMETER && renderVararg) {
            return CodeBlock.of("$T...", typeName(parameterInfo.getParameterType().getElementType()));
        }
        return CodeBlock.of("$T", typeName(parameterInfo.getParameterType()));
    }
}
