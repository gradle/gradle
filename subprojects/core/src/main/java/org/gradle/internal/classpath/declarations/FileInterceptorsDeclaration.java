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

import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.InstanceMethod;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.Receiver;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME)
@SpecificGroovyCallInterceptors(generatedClassName = InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME)
public class FileInterceptorsDeclaration {
    @InterceptCalls
    @InstanceMethod
    public static File[] intercept_listFiles(
        @Receiver File thisFile,
        @CallerClassName String consumer
    ) {
        return Instrumented.fileListFiles(thisFile, consumer);
    }

    @InterceptCalls
    @InstanceMethod
    public static File[] intercept_listFiles(
        @Receiver File thisFile,
        FileFilter fileFilter,
        @CallerClassName String consumer
    ) {
        return Instrumented.fileListFiles(thisFile, fileFilter, consumer);
    }

    @InterceptCalls
    @InstanceMethod
    public static File[] intercept_listFiles(
        @Receiver File thisFile,
        FilenameFilter fileFilter,
        @CallerClassName String consumer
    ) {
        return Instrumented.fileListFiles(thisFile, fileFilter, consumer);
    }

    @InterceptCalls
    @InstanceMethod
    public static boolean intercept_exists(
        @Receiver File thisFile,
        @CallerClassName String consumer
    ) {
        return Instrumented.fileExists(thisFile, consumer);
    }

    @InterceptCalls
    @InstanceMethod
    public static boolean intercept_isFile(
        @Receiver File thisFile,
        @CallerClassName String consumer
    ) {
        return Instrumented.fileIsFile(thisFile, consumer);
    }

    @InterceptCalls
    @InstanceMethod
    public static boolean intercept_isDirectory(
        @Receiver File thisFile,
        @CallerClassName String consumer
    ) {
        return Instrumented.fileIsDirectory(thisFile, consumer);
    }

    @InterceptCalls
    @InstanceMethod
    public static String[] intercept_list(
        @Receiver File thisFile,
        @CallerClassName String consumer
    ) {
        Instrumented.directoryContentObserved(thisFile, consumer);
        return thisFile.list();
    }

    @InterceptCalls
    @InstanceMethod
    public static String[] intercept_list(
        @Receiver File thisFile,
        FilenameFilter filenameFilter,
        @CallerClassName String consumer
    ) {
        Instrumented.directoryContentObserved(thisFile, consumer);
        return thisFile.list(filenameFilter);
    }

    @InterceptCalls
    @InstanceMethod
    public static long intercept_length(
        @Receiver File thisFile,
        @CallerClassName String consumer
    ) {
        Instrumented.fileOpened(thisFile, consumer);
        return thisFile.length();
    }
}
