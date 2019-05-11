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

package org.gradle.api.tasks.testing;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatcher.Junction;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
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
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * The Java agent.
 */
public class UndeclaredIOAgent { // TODO lock from multiple threads or forks
    public static final CharBuffer results = init(); // TODO init from premain only?
    public static CharBuffer init() {
        try {
            return FileChannel.open(Paths.get("build/undeclared.txt"), CREATE, READ, WRITE) // TODO filename should come from test
                .map(READ_WRITE, 0, 100000) // TODO
                .asCharBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The entry point.
     */
    public static void premain(String outputFile, Instrumentation instrumentation) throws Exception {
        instrumentFile(instrumentation);
        instrumentInputFileStream(instrumentation);
    }

    private static void instrumentFile(Instrumentation instrumentation) throws Exception {
        Junction<NamedElement> fileOutputMethodNameMatch = nameMatches("^(?:set|delete|write|create|mkdir|rename).*");
        registerTransformer(instrumentation, File.class, fileOutputMethodNameMatch, FileOutputTransformer.class);

        Junction<NamedElement> fileInputMethodNameMatch = not(fileOutputMethodNameMatch.or(nameMatches("^(?:getName|getPath|compareTo|hashCode|equals|toString|getPath|getParent|isInvalid|getPrefixLength|slashify|createTempFile)$"))); // TODO input list?
        registerTransformer(instrumentation, File.class, fileInputMethodNameMatch, FileInputTransformer.class);
    }

    private static void instrumentInputFileStream(Instrumentation instrumentation) throws Exception {
        Junction<NamedElement> fileInputStreamMatch = named("open");
        registerTransformer(instrumentation, FileInputStream.class, fileInputStreamMatch, FileInputStreamFileTransformer.class);
    }

    private static void registerTransformer(Instrumentation instrumentation, Class<?> type, final Junction<NamedElement> methodMatcher, final Class<?> advice) throws Exception {
        File temp = injectClassesInBootstrap(instrumentation, UndeclaredIOAgent.class, advice);

        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(RedefinitionStrategy.RETRANSFORMATION)
            .ignore(not(nameStartsWith("java.io")))
            .enableBootstrapInjection(instrumentation, temp)
            .type(is(type))
            .transform(new AgentBuilder.Transformer() {
                @Override
                public Builder<?> transform(Builder<?> b, TypeDescription t, ClassLoader c, JavaModule m) {
                    return b.visit(Advice.to(advice).on(methodMatcher));
                }
            })
            .installOn(instrumentation);
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

    // TODO don't write inputs/outputs to same results
    public static class FileOutputTransformer {
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

    public static class FileInputTransformer {
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

    public static class FileInputStreamFileTransformer {
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
}
