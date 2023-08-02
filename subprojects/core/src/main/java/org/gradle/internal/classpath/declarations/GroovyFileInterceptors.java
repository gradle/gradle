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

import groovy.io.FileType;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.GroovyPropertyGetter;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.InstanceMethod;
import org.gradle.internal.instrumentation.api.annotations.InterceptGroovyCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.Receiver;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.features.withstaticreference.WithExtensionReferences;
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE)
@SpecificGroovyCallInterceptors(generatedClassName = InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE)
public class GroovyFileInterceptors {
    @InterceptGroovyCalls
    @GroovyPropertyGetter
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static String intercept_text(
        @Receiver File self,
        @CallerClassName String consumer
    ) throws IOException {
        return Instrumented.groovyFileGetText(self, consumer);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static String intercept_getText(
        @Receiver File self,
        String charset,
        @CallerClassName String consumer
    ) throws IOException {
        return Instrumented.groovyFileGetText(self, charset, consumer);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachByte(
        @Receiver File self,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        ResourceGroovyMethods.eachByte(self, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachByte(
        @Receiver File self,
        int bufferLen,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        ResourceGroovyMethods.eachByte(self, bufferLen, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachDir(
        @Receiver File self,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.directoryContentObserved(self, consumer);
        ResourceGroovyMethods.eachDir(self, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachDirMatch(
        @Receiver File self,
        Object nameFilter,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.directoryContentObserved(self, consumer);
        ResourceGroovyMethods.eachDirMatch(self, nameFilter, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachFile(
        @Receiver File self,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.directoryContentObserved(self, consumer);
        ResourceGroovyMethods.eachFile(self, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachFile(
        @Receiver File self,
        FileType fileType,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.directoryContentObserved(self, consumer);
        ResourceGroovyMethods.eachFile(self, fileType, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachFileMatch(
        @Receiver File self,
        Object nameFilter,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.directoryContentObserved(self, consumer);
        ResourceGroovyMethods.eachFileMatch(self, nameFilter, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachFileMatch(
        @Receiver File self,
        FileType fileType,
        Object nameFilter,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.directoryContentObserved(self, consumer);
        ResourceGroovyMethods.eachFileMatch(self, fileType, nameFilter, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachLine(
        @Receiver File self,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        ResourceGroovyMethods.eachLine(self, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachLine(
        @Receiver File self,
        String charset,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        ResourceGroovyMethods.eachLine(self, charset, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachLine(
        @Receiver File self,
        int firstLine,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        ResourceGroovyMethods.eachLine(self, firstLine, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachLine(
        @Receiver File self,
        String charset,
        int firstLine,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        ResourceGroovyMethods.eachLine(self, charset, firstLine, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static void intercept_eachObject(
        @Receiver File self,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException, ClassNotFoundException {
        Instrumented.fileOpened(self, consumer);
        ResourceGroovyMethods.eachObject(self, closure);
    }

    @InterceptGroovyCalls
    @GroovyPropertyGetter
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static byte[] intercept_bytes(
        @Receiver File self,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.getBytes(self);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static InputStream intercept_newInputStream(
        @Receiver File self,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.newInputStream(self);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static DataInputStream intercept_newDataInputStream(
        @Receiver File self,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.newDataInputStream(self);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static ObjectInputStream intercept_newObjectInputStream(
        @Receiver File self,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.newObjectInputStream(self);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static ObjectInputStream intercept_newObjectInputStream(
        @Receiver File self,
        ClassLoader classLoader,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.newObjectInputStream(self, classLoader);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static BufferedReader intercept_newReader(
        @Receiver File self,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.newReader(self);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static BufferedReader intercept_newReader(
        @Receiver File self,
        String charset,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.newReader(self, charset);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static byte[] intercept_readBytes(
        @Receiver File self,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.readBytes(self);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static List<String> intercept_readLines(
        @Receiver File self,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.readLines(self);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static List<String> intercept_readLines(
        @Receiver File self,
        String charset,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.readLines(self, charset);
    }

    @InterceptGroovyCalls
    @GroovyPropertyGetter
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static long intercept_size(
        @Receiver File self,
        @CallerClassName String consumer
    ) {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.size(self);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static Object intercept_splitEachLine(
        @Receiver File self,
        Pattern pattern,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.splitEachLine(self, pattern, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static Object intercept_splitEachLine(
        @Receiver File self,
        Pattern pattern,
        String charset,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.splitEachLine(self, pattern, charset, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static Object intercept_splitEachLine(
        @Receiver File self,
        String regex,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.splitEachLine(self, regex, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static Object intercept_splitEachLine(
        @Receiver File self,
        String regex,
        String charset,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.splitEachLine(self, regex, charset, closure);
    }


    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static Object intercept_withInputStream(
        @Receiver File self,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.withInputStream(self, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static Object intercept_withDataInputStream(
        @Receiver File self,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.withDataInputStream(self, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static Object intercept_withObjectInputStream(
        @Receiver File self,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.withObjectInputStream(self, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static Object intercept_withObjectInputStream(
        @Receiver File self,
        ClassLoader classLoader,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.withObjectInputStream(self, classLoader, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static Object intercept_withReader(
        @Receiver File self,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.withReader(self, closure);
    }

    @InterceptGroovyCalls
    @InstanceMethod
    @WithExtensionReferences(toClass = ResourceGroovyMethods.class)
    public static Object intercept_withReader(
        @Receiver File self,
        String charset,
        Closure<?> closure,
        @CallerClassName String consumer
    ) throws IOException {
        Instrumented.fileOpened(self, consumer);
        return ResourceGroovyMethods.withReader(self, charset, closure);
    }
}
