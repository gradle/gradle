/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.jvm;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatcher.Junction;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Arrays.stream;
import static java.util.Collections.synchronizedSet;
import static java.util.stream.Collectors.toMap;
import static net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.Target.BOOTSTRAP;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

/**
 * The Java agent.
 *  TODO https://github.com/raphw/byte-buddy/issues/110
 */
public class IOAgent { // TODO lock from multiple threads or forks
    public static final CharBuffer results = init(); // TODO init from premain only?
    public static CharBuffer init() {
        try {
            return FileChannel.open(Paths.get("build/undeclared.txt"), CREATE, READ, WRITE) // TODO filename should come from test
                .map(READ_WRITE, 0, 10000000) // TODO
                .asCharBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The entry point.
     */
    public static void premain(String outputFile, Instrumentation instrumentation) throws Exception {
        Junction<NamedElement> fileMethodNameMatch = nameMatches("^(?:createNewFile|createTempFile|delete|deleteOnExit|mkdir|mkdirs|renameTo|setExecutable|setLastModified|setReadOnly|setReadable|setWritable|writeObject|canExecute|canRead|canWrite|exists|getAbsoluteFile|getAbsolutePath|getCanonicalFile|getCanonicalPath|getFreeSpace|getParentFile|getTotalSpace|getUsableSpace|isAbsolute|isDirectory|isFile|isHidden|lastModified|length|list|listFiles|listRoots|readObject|toPath|toURI|toURL)$");
        registerTransformer(instrumentation, fileMethodNameMatch, is(File.class), FileTransformer.class);

        registerTransformer(instrumentation, named("open"), is(FileInputStream.class), FileInputStreamTransformer.class);

        registerTransformer(instrumentation, named("open"), is(FileOutputStream.class), FileOutputStreamTransformer.class);

        Junction<NamedElement> fileSystemProviderMethodNameMatch = nameMatches("^(?:checkAccess|copy|createDirectory|createLink|createSymbolicLink|delete|deleteIfExists|getFileAttributeView|getFileStore|getFileSystem|getPath|getScheme|installedProviders|isHidden|isSameFile|move|newAsynchronousFileChannel|newByteChannel|newDirectoryStream|newFileChannel|newFileSystem|newInputStream|newOutputStream|readAttributes|readSymbolicLink|setAttribute)$");
        registerTransformer(instrumentation, fileSystemProviderMethodNameMatch, isSubTypeOf(FileSystemProvider.class), FileSystemProviderTransformer.class); // TODO subclasses?
    }

    // TODO compare against watcher
    // TODO could we use this to replace manual IO annotations?

    private static void registerTransformer(
        Instrumentation instrumentation,
        final ElementMatcher<? super MethodDescription> methodMatcher,
        ElementMatcher<? super TypeDescription> typeMatcher,
        final Class<?> advice
    ) throws Exception {
        File temp = injectClassesInBootstrap(instrumentation, IOAgent.class, advice, FileSystemProvider.class);

        new AgentBuilder.Default()
            //.disableClassFormatChanges()
            .with(RedefinitionStrategy.RETRANSFORMATION)
            .with(RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE)
            .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
            .enableBootstrapInjection(instrumentation, temp)
            .ignore(none())
            .type(typeMatcher)
            .transform(
                new AgentBuilder.Transformer() {
                    @Override
                    public Builder<?> transform(Builder<?> b, TypeDescription t, ClassLoader c, JavaModule m) {
                        return b.visit(Advice.to(advice).on(methodMatcher));
                    }
                }
            )
            .installOn(instrumentation);

        instrumentation.retransformClasses(FileSystemProvider.class, advice); // TODO
    }

    private static File injectClassesInBootstrap(Instrumentation instrumentation, Class<?>... classes) throws Exception {
        File temp = createTempDirectory("tmp").toFile();
        temp.deleteOnExit();

        Map<TypeDescription.ForLoadedType, byte[]> types = stream(classes).collect(toMap(
            new Function<Class<?>, TypeDescription.ForLoadedType>() {
                @Override
                public TypeDescription.ForLoadedType apply(Class<?> type) {
                    return new TypeDescription.ForLoadedType(type);
                }
            },
            new Function<Class<?>, byte[]>() {
                @Override
                public byte[] apply(Class<?> type) {
                    return ClassFileLocator.ForClassLoader.read(type);
                }
            })
        );
        ClassInjector.UsingInstrumentation.of(temp, BOOTSTRAP, instrumentation).inject(types);

        return temp;
    }

    public static final Set<String> visited = synchronizedSet(new HashSet<String>(Collections.<String>singletonList(null))); // TODO shared input/output?

    // TODO is copy input & output?
    // TODO don't write inputs/outputs to same results
    public static class FileTransformer {
        @Advice.OnMethodEnter(inline = false)
        public static void enter(@Advice.This(optional = true) File file) {
            String filePath = (file != null) ? file.getPath() : null;
            if (visited.add(filePath)) {
                synchronized (results) {
                    results.put(filePath, 0, filePath.length());
                    results.put('\0');
                }
            }
        }
    }

    public static class FileInputStreamTransformer {
        @Advice.OnMethodEnter(inline = false)
        public static void enter(@Advice.Argument(0) String filePath) {
            if (visited.add(filePath)) {
                synchronized (results) {
                    results.put(filePath, 0, filePath.length());
                    results.put('\0');
                }
            }
        }
    }

    public static class FileOutputStreamTransformer {
        @Advice.OnMethodEnter(inline = false)
        public static void enter(@Advice.Argument(0) String filePath) {
            if (visited.add(filePath)) {
                synchronized (results) {
                    results.put(filePath, 0, filePath.length());
                    results.put('\0');
                }
            }
        }
    }

    public static class FileSystemProviderTransformer {
        @Advice.OnMethodEnter(inline = false)
        public static void enter(@Advice.AllArguments Object[] args) {
            for (Object arg : args) {
                if (arg instanceof Paths) {
                    String filePath = arg.toString();
                    if (visited.add(filePath)) {
                        synchronized (results) {
                            results.put(filePath, 0, filePath.length());
                            results.put('\0');
                        }
                    }
                }
            }
        }
    }
}
