/*
 * Copyright 2026 the original author or authors.
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

import kotlin.io.path.PathsKt;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.StaticMethod;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.KotlinDefaultMask;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration;

import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * Intercepts calls to non-inline Kotlin stdlib {@code kotlin.io.path.*} extensions that open files.
 * <p>
 * Most {@code Path} extensions in kotlin-stdlib are {@code @InlineOnly}, so their bodies are
 * inlined into the user's bytecode and the underlying {@code java.nio.file.Files} calls are
 * caught by the existing NIO interceptors. {@code PathsKt.readText}, however, is a regular
 * (non-inline) function: user bytecode contains an {@code INVOKESTATIC PathsKt.readText} that
 * jumps into kotlin-stdlib bytecode, and since stdlib is not on the instrumented classpath the
 * transitive {@code Files.newBufferedReader} call is invisible to the configuration cache input
 * tracker. We therefore need to intercept the extension at its call site.
 *
 * @see <a href="https://github.com/gradle/gradle/issues/33704">gradle/gradle#33704</a>
 */
@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE)
@SpecificGroovyCallInterceptors(generatedClassName = InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE)
public class KotlinPathExtensionsInterceptorsDeclaration {

    @InterceptCalls
    @StaticMethod(ofClass = PathsKt.class)
    public static String intercept_readText(
        Path thisPath,
        Charset charset,
        @KotlinDefaultMask int mask,
        @CallerClassName String consumer
    ) throws Throwable {
        return mask != 0 ?
            Instrumented.kotlinIoPathsKtReadTextDefault(thisPath, charset, mask, null, consumer) :
            Instrumented.kotlinIoPathsKtReadText(thisPath, charset, consumer);
    }
}
