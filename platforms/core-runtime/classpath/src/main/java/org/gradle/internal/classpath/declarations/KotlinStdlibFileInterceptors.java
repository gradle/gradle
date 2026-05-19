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
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.StaticMethod;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.KotlinDefaultMask;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration;
import org.gradle.internal.lazy.Lazy;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.nio.charset.Charset;
import java.util.List;

import static org.gradle.internal.classpath.MethodHandleUtils.invokeKotlinStaticDefault;
import static org.gradle.internal.classpath.MethodHandleUtils.lazyKotlinStaticDefaultHandle;
import static org.gradle.internal.classpath.declarations.Handles.FOR_EACH_LINE_DEFAULT;
import static org.gradle.internal.classpath.declarations.Handles.READ_LINES_DEFAULT;
import static org.gradle.internal.classpath.declarations.Handles.USE_LINES_DEFAULT;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE)
@SpecificGroovyCallInterceptors(generatedClassName = InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE)
public class KotlinStdlibFileInterceptors {
    @InterceptCalls
    @StaticMethod(ofClass = FilesKt.class)
    public static byte[] intercept_readBytes(
        File self,
        @CallerClassName String consumer
    ) {
        Instrumented.fileOpened(self, consumer);
        return FilesKt.readBytes(self);
    }

    @InterceptCalls
    @StaticMethod(ofClass = FilesKt.class)
    public static void intercept_forEachBlock(
        File self,
        Function2<?, ?, ?> action,
        @CallerClassName String consumer
    ) {
        Instrumented.fileOpened(self, consumer);
        FilesKt.forEachBlock(self, Cast.uncheckedNonnullCast(action));
    }

    @InterceptCalls
    @StaticMethod(ofClass = FilesKt.class)
    public static void intercept_forEachBlock(
        File self,
        int blockSize,
        Function2<?, ?, ?> action,
        @CallerClassName String consumer
    ) {
        Instrumented.fileOpened(self, consumer);
        FilesKt.forEachBlock(self, blockSize, Cast.uncheckedNonnullCast(action));
    }

    @InterceptCalls
    @StaticMethod(ofClass = FilesKt.class)
    public static void intercept_forEachLine(
        File self,
        Charset charset,
        Function1<?, ?> action,
        @KotlinDefaultMask int mask,
        @CallerClassName String consumer
    ) throws Throwable {
        Instrumented.fileOpened(self, consumer);
        if (mask == 0) {
            FilesKt.forEachLine(self, charset, Cast.uncheckedNonnullCast(action));
        } else {
            invokeKotlinStaticDefault(FOR_EACH_LINE_DEFAULT, mask, self, charset, action);
        }
    }

    @InterceptCalls
    @StaticMethod(ofClass = FilesKt.class)
    public static List<String> intercept_readLines(
        File self,
        Charset charset,
        @KotlinDefaultMask int mask,
        @CallerClassName String consumer
    ) throws Throwable {
        Instrumented.fileOpened(self, consumer);
        return mask == 0
            ? FilesKt.readLines(self, charset)
            : invokeKotlinStaticDefault(READ_LINES_DEFAULT, mask, self, charset);
    }

    @InterceptCalls
    @StaticMethod(ofClass = FilesKt.class)
    public static Object intercept_useLines(
        File self,
        Charset charset,
        Function1<?, ?> block,
        @KotlinDefaultMask int mask,
        @CallerClassName String consumer
    ) throws Throwable {
        Instrumented.fileOpened(self, consumer);
        return mask == 0
            ? FilesKt.useLines(self, charset, Cast.uncheckedNonnullCast(block))
            : invokeKotlinStaticDefault(USE_LINES_DEFAULT, mask, self, charset, block);
    }

}

class Handles {
    public static final Lazy<MethodHandle> FOR_EACH_LINE_DEFAULT =
        lazyKotlinStaticDefaultHandle(FilesKt.class, "forEachLine", void.class, File.class, Charset.class, Function1.class);

    public static final Lazy<MethodHandle> READ_LINES_DEFAULT =
        lazyKotlinStaticDefaultHandle(FilesKt.class, "readLines", List.class, File.class, Charset.class);

    public static final Lazy<MethodHandle> USE_LINES_DEFAULT =
        lazyKotlinStaticDefaultHandle(FilesKt.class, "useLines", Object.class, File.class, Charset.class, Function1.class);
}
