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
package org.gradle.testretry.internal.testsreader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.objectweb.asm.Opcodes.ASM7;

public final class TestsReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestsReader.class);

    private final Set<File> testClassesDirs;
    private final Iterable<File> classpath;

    public TestsReader(Set<File> testClassesDirs, Iterable<File> classpath) {
        this.testClassesDirs = testClassesDirs;
        this.classpath = classpath;
    }

    // Finds classes only within the testClassesDir
    public <R> Optional<R> readTestClassDirClass(String className, Supplier<? extends Visitor<R>> factory) {
        return testClassesDirs.stream()
            .map(dir -> new File(dir, classFileName(className)))
            .filter(File::exists)
            .findFirst()
            .map(file -> visitClassFile(file, factory.get()));
    }

    public <R> Optional<R> readClass(String className, Supplier<? extends Visitor<R>> factory) {
        Optional<R> opt = readTestClassDirClass(className, factory);
        if (opt.isPresent()) {
            return opt;
        } else {
            return readClasspathClass(className, factory);
        }
    }

    @Nullable
    private <R> R visitClassFile(File file, Visitor<R> visitor) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            return visit(in, visitor);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (IllegalArgumentException iae) {
            if (iae.getMessage().startsWith("Unsupported class file major version")) {
                LOGGER.warn("Could not parse class, ignoring for retry", iae);
                return null;
            }
            throw iae;
        }
    }

    private <R> R visit(InputStream in, Visitor<R> visitor) throws IOException {
        ClassReader classReader = new ClassReader(in);
        classReader.accept(visitor, 0);
        return visitor.getResult();
    }
    // Finds classes within the testClassesDir and the rest of the classpath

    private <R> Optional<R> readClasspathClass(String className, Supplier<? extends Visitor<R>> factory) {
        String classFileName = classFileName(className);
        for (File file : classpath) {
            if (!file.exists()) {
                continue;
            }

            if (file.isDirectory()) {
                File classFile = new File(file, classFileName);
                if (classFile.exists()) {
                    return Optional.ofNullable(visitClassFile(classFile, factory.get()));
                } else {
                    continue;
                }
            }

            if (!file.getName().endsWith(".jar")) {
                continue;
            }

            try (JarFile jarFile = new JarFile(file)) {
                Optional<JarEntry> classFile = jarFile.stream()
                    .filter(maybeClass -> maybeClass.getName().equals(classFileName))
                    .findAny();

                if (classFile.isPresent()) {
                    try (InputStream is = jarFile.getInputStream(classFile.get())) {
                        return Optional.of(visit(is, factory.get()));
                    }
                }
            } catch (IOException ignored) {
                // we tried... this file looks corrupt, move on to the next jar
            }
        }

        return Optional.empty();
    }

    @NotNull
    private String classFileName(String className) {
        return className.replace('.', '/') + ".class";
    }

    public abstract static class Visitor<T> extends ClassVisitor {

        public Visitor() {
            super(ASM7);
        }

        public abstract T getResult();

    }

}
