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
import org.gradle.internal.instrumentation.api.annotations.InterceptGroovyCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = CompositeCallInterceptionTestInterceptorsDeclaration.JVM_BYTECODE_GENERATED_CLASS)
@SpecificGroovyCallInterceptors(generatedClassName = CompositeCallInterceptionTestInterceptorsDeclaration.GROOVY_GENERATED_CLASS)
public class CompositeCallInterceptionTestInterceptorsDeclaration {

    /**
     * The generated class name has to be different from the one used in {@link BasicCallInterceptionTestInterceptorsDeclaration}
     * so that the generated class is different and we can test {@link CompositeInterceptorTestReceiver}.
     */
    public static final String JVM_BYTECODE_GENERATED_CLASS = BasicCallInterceptionTestInterceptorsDeclaration.JVM_BYTECODE_GENERATED_CLASS + "_composite";
    public static final String GROOVY_GENERATED_CLASS = BasicCallInterceptionTestInterceptorsDeclaration.GROOVY_GENERATED_CLASS + "_composite";

    @InterceptCalls
    @CallableKind.InstanceMethod
    public static void intercept_test(
        @ParameterKind.Receiver CompositeInterceptorTestReceiver self,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "composite.test()";
        self.test();
    }

    @InterceptCalls
    @CallableKind.InstanceMethod
    public static void intercept_test(
        @ParameterKind.Receiver CompositeInterceptorTestReceiver self,
        CompositeInterceptorTestReceiver arg0,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "composite.test(InterceptorTestReceiver)";
        self.test(arg0);
    }

    @InterceptCalls
    @CallableKind.InstanceMethod
    public static void intercept_testVararg(
        @ParameterKind.Receiver CompositeInterceptorTestReceiver self,
        @ParameterKind.VarargParameter Object[] arg,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "composite.testVararg(Object...)";
        self.testVararg(arg);
    }

    @InterceptCalls
    @CallableKind.InstanceMethod
    public static void intercept_nonExistent(
        @ParameterKind.Receiver CompositeInterceptorTestReceiver self,
        String parameter,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "composite.nonExistent(String)-non-existent";
    }

    @InterceptGroovyCalls
    @CallableKind.GroovyPropertyGetter
    public static String intercept_testString(
        @ParameterKind.Receiver CompositeInterceptorTestReceiver self,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "composite.getTestString()";
        return self.getTestString() + "-intercepted";
    }

    @InterceptGroovyCalls
    @CallableKind.GroovyPropertySetter
    public static void intercept_testString(
        @ParameterKind.Receiver CompositeInterceptorTestReceiver self,
        String newValue,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "composite.setTestString(String)";
        self.setTestString(newValue);
    }

    @InterceptGroovyCalls
    @CallableKind.GroovyPropertyGetter
    public static boolean intercept_testFlag(
        @ParameterKind.Receiver CompositeInterceptorTestReceiver self,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "composite.isTestFlag()";
        return self.isTestFlag();
    }

    @InterceptGroovyCalls
    @CallableKind.GroovyPropertySetter
    public static void intercept_testFlag(
        @ParameterKind.Receiver CompositeInterceptorTestReceiver self,
        boolean newValue,
        @ParameterKind.CallerClassName String consumer
    ) {
        self.intercepted = "composite.setTestFlag(boolean)";
        self.setTestFlag(newValue);
    }

    @InterceptGroovyCalls
    @CallableKind.GroovyPropertyGetter
    public static String intercept_nonExistentProperty(
        @ParameterKind.Receiver CompositeInterceptorTestReceiver self
    ) {
        self.intercepted = "composite.getNonExistentProperty()-non-existent";
        return "nonExistent";
    }

    @InterceptGroovyCalls
    @CallableKind.GroovyPropertySetter
    public static void intercept_nonExistentProperty(
        @ParameterKind.Receiver CompositeInterceptorTestReceiver self,
        String value
    ) {
        self.intercepted = "composite.setNonExistentProperty(String)-non-existent";
    }
}
