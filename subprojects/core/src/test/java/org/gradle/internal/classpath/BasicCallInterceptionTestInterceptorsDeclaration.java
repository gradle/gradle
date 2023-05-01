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

package org.gradle.internal.classpath;

import org.gradle.internal.instrumentation.api.annotations.CallableKind;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = BasicCallInterceptionTestInterceptorsDeclaration.JVM_BYTECODE_GENERATED_CLASS)
@SpecificGroovyCallInterceptors(generatedClassName = BasicCallInterceptionTestInterceptorsDeclaration.GROOVY_GENERATED_CLASS)
public class BasicCallInterceptionTestInterceptorsDeclaration {
    public static final String JVM_BYTECODE_GENERATED_CLASS = "org.gradle.internal.classpath.Test_interceptors_jvmbytecode_generated";
    public static final String GROOVY_GENERATED_CLASS = "org.gradle.internal.classpath.Test_interceptors_groovy_generated";

    @InterceptCalls
    @CallableKind.InstanceMethod
    public static void intercept_call(
        @ParameterKind.Receiver InterceptorTestReceiver self,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "call()";
        self.call();
    }

    @InterceptCalls
    @CallableKind.InstanceMethod
    public static void intercept_call(
        @ParameterKind.Receiver InterceptorTestReceiver self,
        InterceptorTestReceiver arg0,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "call(InterceptorTestReceiver)";
        self.call(arg0);
    }

    @InterceptCalls
    @CallableKind.InstanceMethod
    public static void intercept_callVararg(
        @ParameterKind.Receiver InterceptorTestReceiver self,
        @ParameterKind.VarargParameter Object[] arg,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "callVararg(Object...)";
        self.callVararg(arg);
    }

    @InterceptCalls
    @CallableKind.InstanceMethod
    public static void intercept_nonExistent(
        @ParameterKind.Receiver InterceptorTestReceiver self,
        String parameter,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "nonExistent(String)-non-existent";
    }
}
