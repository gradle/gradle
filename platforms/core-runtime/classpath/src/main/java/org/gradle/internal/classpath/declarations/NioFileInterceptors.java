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
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.nio.file.spi.FileTypeDetector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static java.nio.file.Files.newBufferedReader;
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
        return newBufferedReader(path);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static BufferedReader intercept_newBufferedReader(
        Path path,
        Charset charset,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return newBufferedReader(path, charset);
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
    @SuppressWarnings("StreamResourceLeak") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    public static Stream<String> intercept_lines(
        Path path,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportFileOpened(path, consumer);
        return Files.lines(path);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    @SuppressWarnings("StreamResourceLeak") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
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
    @SuppressWarnings("StreamResourceLeak") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    public static DirectoryStream<Path> intercept_newDirectoryStream(
        Path path,
        @CallerClassName String consumer
    ) throws IOException {
        tryReportDirectoryContentObserved(path, consumer);
        return Files.newDirectoryStream(path);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    @SuppressWarnings("StreamResourceLeak") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
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
    @SuppressWarnings("StreamResourceLeak") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
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
    @SuppressWarnings("StreamResourceLeak") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
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

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static Path intercept_walkFileTree(
        Path start,
        FileVisitor<?> visitor,
        @CallerClassName String consumer
    ) throws IOException {
        return Files.walkFileTree(start, new RecordingFileVisitor(Cast.uncheckedNonnullCast(visitor), consumer));
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static Path intercept_walkFileTree(
        Path start,
        Set<?> options, // Set<FileVisitOption>, using Set<?> to match existing interceptor style
        int maxDepth,
        FileVisitor<?> visitor,
        @CallerClassName String consumer
    ) throws IOException {
        return Files.walkFileTree(
            start,
            Cast.uncheckedNonnullCast(options),
            maxDepth,
            new RecordingFileVisitor(Cast.uncheckedNonnullCast(visitor), consumer)
        );
    }

    /**
     * Reimplements {@link Files#walk} on top of {@link Files#walkFileTree} so that each entry is
     * classified using the {@link BasicFileAttributes} supplied by the walk itself, which honors
     * the caller's {@link FileVisitOption#FOLLOW_LINKS} setting. Delegating to {@link Files#walk}
     * and calling {@link Files#isDirectory(Path, java.nio.file.LinkOption...)} on each result would
     * misclassify a symlink-to-directory as a directory (registering a directory-content fingerprint
     * we did not actually observe) when the caller walked without {@code FOLLOW_LINKS}.
     */
    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static Stream<Path> intercept_walk(
        Path start,
        @VarargParameter FileVisitOption[] options,
        @CallerClassName String consumer
    ) throws IOException {
        return walkAndRecord(start, Integer.MAX_VALUE, options, consumer);
    }

    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static Stream<Path> intercept_walk(
        Path start,
        int maxDepth,
        @VarargParameter FileVisitOption[] options,
        @CallerClassName String consumer
    ) throws IOException {
        return walkAndRecord(start, maxDepth, options, consumer);
    }

    private static Stream<Path> walkAndRecord(
        Path start,
        int maxDepth,
        FileVisitOption[] options,
        String consumer
    ) throws IOException {
        WalkRecordingFileVisitor visitor = new WalkRecordingFileVisitor(consumer);
        Set<FileVisitOption> optionSet = options.length == 0
            ? EnumSet.noneOf(FileVisitOption.class)
            : EnumSet.copyOf(Arrays.asList(options));
        Files.walkFileTree(start, optionSet, maxDepth, visitor);
        return visitor.collected.stream();
    }

    /**
     * Reimplements {@link Files#find} on top of {@link Files#walkFileTree} so that every directory
     * whose contents are consulted during the walk is registered as a configuration cache input,
     * even when the user-supplied {@code matcher} filters it (or all of its children) out.
     */
    @InterceptCalls
    @StaticMethod(ofClass = Files.class)
    public static Stream<Path> intercept_find(
        Path start,
        int maxDepth,
        BiPredicate<Path, BasicFileAttributes> matcher,
        @VarargParameter FileVisitOption[] options,
        @CallerClassName String consumer
    ) throws IOException {
        FindRecordingFileVisitor visitor = new FindRecordingFileVisitor(matcher, consumer);
        Set<FileVisitOption> optionSet = options.length == 0
            ? EnumSet.noneOf(FileVisitOption.class)
            : EnumSet.copyOf(Arrays.asList(options));
        Files.walkFileTree(start, optionSet, maxDepth, visitor);
        return visitor.collected.stream();
    }

    /**
     * A {@link FileVisitor} that records filesystem observations against the configuration cache
     * before delegating each visit callback to a user-supplied visitor.
     */
    private static final class RecordingFileVisitor implements FileVisitor<Path> {
        private final FileVisitor<Path> delegate;
        private final String consumer;

        RecordingFileVisitor(FileVisitor<Path> delegate, String consumer) {
            this.delegate = delegate;
            this.consumer = consumer;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            tryReportDirectoryContentObserved(dir, consumer);
            return delegate.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            tryReportFileSystemEntryObserved(file, consumer);
            return delegate.visitFile(file, attrs);
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            tryReportFileSystemEntryObserved(file, consumer);
            return delegate.visitFileFailed(file, exc);
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return delegate.postVisitDirectory(dir, exc);
        }
    }

    /**
     * A {@link FileVisitor} that records filesystem observations against the configuration cache
     * and collects paths matching the user-supplied matcher, used to reimplement {@link Files#find}.
     */
    private static final class FindRecordingFileVisitor implements FileVisitor<Path> {
        private final BiPredicate<Path, BasicFileAttributes> matcher;
        private final String consumer;
        final List<Path> collected = new ArrayList<>();

        FindRecordingFileVisitor(BiPredicate<Path, BasicFileAttributes> matcher, String consumer) {
            this.matcher = matcher;
            this.consumer = consumer;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            tryReportDirectoryContentObserved(dir, consumer);
            if (matcher.test(dir, attrs)) {
                collected.add(dir);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            tryReportFileSystemEntryObserved(file, consumer);
            if (matcher.test(file, attrs)) {
                collected.add(file);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            throw exc;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) {
                throw exc;
            }
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * A {@link FileVisitor} that records filesystem observations against the configuration cache
     * and collects every visited path in walk order, used to reimplement {@link Files#walk}.
     * Relies on the {@link BasicFileAttributes} supplied by the walk to classify each entry, so
     * symlinks are recorded as leaves when the caller walked without {@code FOLLOW_LINKS} and as
     * directories when they were followed.
     */
    private static final class WalkRecordingFileVisitor implements FileVisitor<Path> {
        private final String consumer;
        final List<Path> collected = new ArrayList<>();

        WalkRecordingFileVisitor(String consumer) {
            this.consumer = consumer;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            tryReportDirectoryContentObserved(dir, consumer);
            collected.add(dir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            tryReportFileSystemEntryObserved(file, consumer);
            collected.add(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            throw exc;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) {
                throw exc;
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
