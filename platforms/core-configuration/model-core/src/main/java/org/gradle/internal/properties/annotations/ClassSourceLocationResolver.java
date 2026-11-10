/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.properties.annotations;

import org.gradle.model.internal.asm.AsmConstants;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves source file names and method line numbers from class bytecode using ASM.
 */
public class ClassSourceLocationResolver {
    public static final ClassSourceLocationResolver INSTANCE = new ClassSourceLocationResolver();

    private final ClassValue<ClassSourceLocation> cache;

    public ClassSourceLocationResolver() {
        this.cache = new ClassValue<ClassSourceLocation>() {
            @Override
            protected ClassSourceLocation computeValue(Class<?> type) {
                return parseClass(type);
            }
        };
    }

    /**
     * Resolves source location metadata for the given class.
     */
    public ClassSourceLocation resolve(@Nullable Class<?> clazz) {
        if (clazz == null) {
            return ClassSourceLocation.UNKNOWN;
        }
        return cache.get(clazz);
    }

    /**
     * Resolves source location metadata for the declaring class of the given method.
     */
    public ClassSourceLocation resolve(@Nullable Method method) {
        if (method == null) {
            return ClassSourceLocation.UNKNOWN;
        }
        return resolve(method.getDeclaringClass());
    }

    private static ClassSourceLocation parseClass(Class<?> clazz) {
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        ClassLoader classLoader = clazz.getClassLoader();
        InputStream in = null;
        if (classLoader != null) {
            in = classLoader.getResourceAsStream(resourceName);
        }
        if (in == null) {
            in = ClassLoader.getSystemResourceAsStream(resourceName);
        }
        if (in == null) {
            return ClassSourceLocation.UNKNOWN;
        }
        try (InputStream stream = in) {
            ClassReader reader = new ClassReader(stream);
            SourceLocationClassVisitor visitor = new SourceLocationClassVisitor();
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            return visitor.toClassSourceLocation();
        } catch (Throwable t) {
            return ClassSourceLocation.UNKNOWN;
        }
    }

    private static class SourceLocationClassVisitor extends ClassVisitor {
        @Nullable
        private String sourceFilename;
        private final Map<String, Integer> lineNumbers = new HashMap<>();

        SourceLocationClassVisitor() {
            super(AsmConstants.ASM_LEVEL);
        }

        @Override
        public void visitSource(@Nullable String source, @Nullable String debug) {
            this.sourceFilename = source;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, @Nullable String signature, String @Nullable [] exceptions) {
            return new MethodVisitor(AsmConstants.ASM_LEVEL) {
                private boolean firstLineVisited = false;

                @Override
                public void visitLineNumber(int line, Label start) {
                    if (!firstLineVisited) {
                        firstLineVisited = true;
                        lineNumbers.put(ClassSourceLocation.methodKey(name, descriptor), line);
                    }
                }
            };
        }

        ClassSourceLocation toClassSourceLocation() {
            return new ClassSourceLocation(sourceFilename, lineNumbers);
        }
    }
}
