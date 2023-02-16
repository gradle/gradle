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

import kotlin.io.FilesKt;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.StaticMethod;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.KotlinDefaultMask;

import java.io.File;
import java.nio.charset.Charset;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME)
@SpecificGroovyCallInterceptors(generatedClassName = InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME)
public class KotlinFileExtensionsInterceptorsDeclaration {

    @InterceptCalls
    @StaticMethod(ofClass = FilesKt.class)
    public static String intercept_readText(
        File thisFile,
        Charset charset,
        @KotlinDefaultMask int mask,
        @CallerClassName String consumer
    ) throws Throwable {
        return mask != 0 ?
            Instrumented.kotlinIoFilesKtReadTextDefault(thisFile, charset, mask, null, consumer) :
            Instrumented.kotlinIoFilesKtReadText(thisFile, charset, consumer);
    }
}
