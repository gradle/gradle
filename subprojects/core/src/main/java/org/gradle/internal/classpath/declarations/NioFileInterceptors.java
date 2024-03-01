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

import org.gradle.internal.Cast;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.InstanceMethod;
import org.gradle.internal.instrumentation.api.annotations.CallableKind.StaticMethod;
import org.gradle.internal.instrumentation.api.annotations.InterceptCalls;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.Receiver;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.VarargParameter;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.nio.file.spi.FileTypeDetector;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.gradle.internal.classpath.FileUtils.optionsAllowReading;
import static org.gradle.internal.classpath.FileUtils.tryReportDirectoryContentObserved;
import static org.gradle.internal.classpath.FileUtils.tryReportFileOpened;
import static org.gradle.internal.classpath.FileUtils.tryReportFileSystemEntryObserved;

@SuppressWarnings("NewMethodNamingConvention")
@SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE)
@SpecificGroovyCallInterceptors(generatedClassName = InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_CONFIG_CACHE)
public class NioFileInterceptors {
    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static boolean intercept_isRegularFile(
        Path path,
        @VarargParameter LinkOption[] options,
        @CallerClassName String consumer
    ) {
        tryReportFileSystemEntryObserved(path, consumer);
        return Files.isRegularFile(path, options);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static boolean intercept_isDirectory(
        Path path,
        @VarargParameter LinkOption[] options,
        @CallerClassName String consumer
    ) {
        tryReportFileSystemEntryObserved(path, consumer);
        return Files.isDirectory(path, options);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static boolean intercept_exists(
        Path path,
        @VarargParameter LinkOption[] options,
        @CallerClassName String consumer
    ) {
        tryReportFileSystemEntryObserved(path, consumer);
        return Files.exists(path, options);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static boolean intercept_notExists(
        Path path,
        @VarargParameter LinkOption[] options,
        @CallerClassName String consumer
    ) {
        tryReportFileSystemEntryObserved(path, consumer);
        return Files.notExists(path, options);
    }

    // TODO: handle varargs in Groovy
    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static SeekableByteChannel intercept_newByteChannel(
        Path path,
        @VarargParameter OpenOption[] options,
        @CallerClassName String consumer
    ) throws IOException {
        if (optionsAllowReading(options)) {
            tryReportFileOpened(path, consumer);
        }
        return Files.newByteChannel(path, options);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static SeekableByteChannel intercept_newByteChannel(
        Path path,
        Set<?> options, // todo: use a proper type argument here once the tool supports it
        @VarargParameter FileAttribute<?>[] attrs,
        @CallerClassName String consumer
    ) throws IOException {
        if (optionsAllowReading(options)) {
            tryReportFileOpened(path, consumer);
        }
        return Files.newByteChannel(path, Cast.uncheckedNonnullCast(options), attrs);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static BufferedReader intercept_newBufferedReader(
        Path path,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return Files.newBufferedReader(path);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static BufferedReader intercept_newBufferedReader(
        Path path,
        Charset charset,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return Files.newBufferedReader(path, charset);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static InputStream intercept_newInputStream(
        Path path,
        @VarargParameter OpenOption[] options,
        @CallerClassName String consumer
    ) throws IOException {
        if (optionsAllowReading(options)) {
            tryReportFileOpened(path, consumer);
        }
        return Files.newInputStream(path, options);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static String intercept_readString(
        Path path,
        @CallerClassName String consumer
    ) throws Throwable {
        return Instrumented.filesReadString(path, consumer);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static String intercept_readString(
        Path path,
        Charset charset,
        @CallerClassName String consumer
    ) throws Throwable {
        return Instrumented.filesReadString(path, charset, consumer);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static byte[] intercept_readAllBytes(
        Path path,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return Files.readAllBytes(path);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static List<String> intercept_readAllLines(
        Path path,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return Files.readAllLines(path);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static List<String> intercept_readAllLines(
        Path path,
        Charset charset,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return Files.readAllLines(path, charset);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static Stream<String> intercept_lines(
        Path path,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return Files.lines(path);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static Stream<String> intercept_lines(
        Path path,
        Charset charset,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return Files.lines(path, charset);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static DirectoryStream<Path> intercept_newDirectoryStream(
        Path path,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportDirectoryContentObserved(path, consumer);
        return Files.newDirectoryStream(path);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static DirectoryStream<Path> intercept_newDirectoryStream(
        Path path,
        String glob,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportDirectoryContentObserved(path, consumer);
        return Files.newDirectoryStream(path, glob);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static DirectoryStream<Path> intercept_newDirectoryStream(
        Path path,
        DirectoryStream.Filter<?> filter,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportDirectoryContentObserved(path, consumer);
        return Files.newDirectoryStream(path, Cast.<DirectoryStream.Filter<? super Path>>uncheckedNonnullCast(filter));
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static Stream<Path> intercept_list(
        Path path,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportDirectoryContentObserved(path, consumer);
        return Files.list(path);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static String intercept_probeContentType(
        Path path,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return Files.probeContentType(path);
    }

    @InterceptCalls
    @InstanceMethod
    public static String intercept_probeContentType(
        @Receiver FileTypeDetector self,
        Path path,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return self.probeContentType(path);
    }

    @InterceptCalls
    @InstanceMethod
    public static SeekableByteChannel intercept_newByteChannel(
        @Receiver FileSystemProvider self,
        Path path,
        Set<?> options,
        @VarargParameter FileAttribute<?>[] attrs,
        @CallerClassName String consumer
    ) throws IOException {
        if (optionsAllowReading(options)) {
            tryReportFileOpened(path, consumer);
        }
        return self.newByteChannel(path, Cast.uncheckedCast(options), attrs);
    }

    @InterceptCalls
    @InstanceMethod
    public static InputStream intercept_newInputStream(
        @Receiver FileSystemProvider self,
        Path path,
        @VarargParameter OpenOption[] options,
        @CallerClassName String consumer
    ) throws IOException {
        if (optionsAllowReading(options)) {
            tryReportFileOpened(path, consumer);
        }
        return self.newInputStream(path, options);
    }

    @InterceptCalls
    @InstanceMethod
    public static DirectoryStream<Path> intercept_newDirectoryStream(@Receiver FileSystemProvider self,
        Path path,
        DirectoryStream.Filter<?> filter,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportDirectoryContentObserved(path, consumer);
        return self.newDirectoryStream(path, Cast.uncheckedCast(filter));
    }

    @InterceptCalls
    @StaticMethod(ofClass = FileChannel.class)
    public static FileChannel intercept_open(
        Path path,
        @VarargParameter OpenOption[] options,
        @CallerClassName String consumer
    ) throws IOException {
        if (optionsAllowReading(options)) {
            tryReportFileOpened(path, consumer);
        }
        return FileChannel.open(path, options);
    }

    @InterceptCalls
    @StaticMethod(ofClass = FileChannel.class)
    public static FileChannel intercept_open(
        Path path,
        Set<?> options,
        @VarargParameter FileAttribute<?>[] attrs,
        @CallerClassName String consumer
    ) throws IOException {
        if (optionsAllowReading(options)) {
            tryReportFileOpened(path, consumer);
        }
        return FileChannel.open(path, Cast.uncheckedCast(options), attrs);
    }
}
