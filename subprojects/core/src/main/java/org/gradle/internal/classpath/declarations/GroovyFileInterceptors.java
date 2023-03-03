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

package org.gradle.internal.classpath.declarations;

import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.instrumentation.api.annotations.CallableDefinition.Name;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.GroovyProperty;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.InstanceMethod;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.StaticMethod;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.InterceptGroovyCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.Receiver;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME)
@SpecificGroovyCallInterceptors(generatedClassName = InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME)
public class GroovyFileInterceptors {
    @InterceptGroovyCalls
    @GroovyProperty
    public static String intercept_text(
        @Receiver File self,
        @CallerClassName String consumer
    ) throws IOException {
        return Instrumented.groovyFileGetText(self, consumer);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    public static String intercept_getText(
        @Receiver File self,
        String charset,
        @CallerClassName String consumer
    ) throws IOException {
        return Instrumented.groovyFileGetText(self, charset, consumer);
    }

    @InterceptCalls
    @StaticMethod(ofClass = ResourceGroovyMethods.class)
    @Name("getText")
    public static String interceptStaticallyCompiledGetText(
        File file,
        @CallerClassName String consumer
    ) throws IOException {
        return Instrumented.groovyFileGetText(file, consumer);
    }

    @InterceptCalls
    @StaticMethod(ofClass = ResourceGroovyMethods.class)
    @Name("getText")
    public static String interceptStaticallyCompiledGetText(
        File file,
        String charset,
        @CallerClassName String consumer
    ) throws IOException {
        return Instrumented.groovyFileGetText(file, charset, consumer);
    }
}
