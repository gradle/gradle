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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
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
import static java.util.stream.Collectors.toMap;
import static net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.Target.BOOTSTRAP;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * The Java agent.
 */
public class UndeclaredIOAgent {
    public static CharBuffer results = init();

    public static CharBuffer init() {
        try {
            return FileChannel.open(Paths.get("build/undeclared.txt"), CREATE, READ, WRITE) // TODO
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
        File temp = injectClassesInBootstrap(instrumentation, UndeclaredIOAgent.class);

        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(RedefinitionStrategy.RETRANSFORMATION)
            .ignore(not(nameStartsWith("java.io")))
            .enableBootstrapInjection(instrumentation, temp)
            .type(is(File.class))
            .transform(new AgentBuilder.Transformer() {
                @Override
                public Builder<?> transform(Builder<?> b, TypeDescription t, ClassLoader c, JavaModule m) {
                    return b.visit(Advice.to(UndeclaredIOAgent.class)
                        .on(not(named("getPath"))));
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

    public static final Set<String> visited = new HashSet<String>();

    /**
     * The inlined input capturing code.
     */
    @Advice.OnMethodEnter(inline = true)
    public static void enter(@Advice.This(optional = true) File file) {
        if (file != null) {
            String filePath = file.getPath();
            if (visited.add(filePath)) {
                results.put(filePath, 0, filePath.length());
                results.put('\0');
            }
        }
    }
}
