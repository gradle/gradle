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

package org.gradle.internal.instrumentation.property.upgrades;

import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.InstanceMethod;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.Receiver;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES)
@SpecificGroovyCallInterceptors(generatedClassName = InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES)
public class CompileOptionsInterceptorsDeclaration {

    @InterceptCalls
    @InstanceMethod
    public static CompileOptions intercept_setIncremental(
        @Receiver CompileOptions compileOptions,
        boolean incremental
    ) {
        compileOptions.getIncremental().set(incremental);
        return compileOptions;
    }

    @InterceptCalls
    @InstanceMethod
    public static boolean intercept_isIncremental(
        @Receiver CompileOptions compileOptions
    ) {
        return compileOptions.getIncremental().getOrElse(false);
    }
}
