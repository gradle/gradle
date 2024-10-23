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

package org.gradle.internal.classpath;

import org.gradle.api.file.RelativePath;
import org.gradle.internal.Pair;
import org.gradle.internal.classloader.TransformingClassLoader;
import org.gradle.internal.classpath.transforms.ClassTransform;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.function.Predicate;

public class TestInstrumentedClassLoader extends TransformingClassLoader {
    private final ClassTransform transform;
    private final Predicate<String> shouldLoadTransformedClass;
    private final ClassLoader source;

    TestInstrumentedClassLoader(
        ClassLoader source,
        Predicate<String> shouldLoadTransformedClass,
        ClassTransform transform
    ) {
        super("test-transformed-loader", source, Collections.emptyList());
        this.shouldLoadTransformedClass = shouldLoadTransformedClass;
        this.transform = transform;
        this.source = source;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (shouldLoadTransformedClass.test(name)) {
            Class<?> result = findLoadedClass(name);
            if (result == null) {
                result = findClass(name);
            }
            if (resolve) {
                resolveClass(result);
            }
            return result;
        }
        return super.loadClass(name, resolve);
    }

    @Override
    public URL findResource(String name) {
        return source.getResource(name);
    }

    @Override
    protected @Nonnull byte[] transform(String className, @Nonnull byte[] bytes) {
        String path = className.replace(".", "/") + ".class";
        ClasspathEntryVisitor.Entry classEntry = new ClasspathEntryVisitor.Entry() {
            @Override
            public String getName() {
                return className;
            }

            @Override
            public RelativePath getPath() {
                return RelativePath.parse(true, path);
            }

            @Override
            public ClasspathEntryVisitor.Entry.CompressionMethod getCompressionMethod() {
                return ClasspathEntryVisitor.Entry.CompressionMethod.STORED;
            }

            @Override
            public byte[] getContent() {
                return bytes;
            }
        };
        ClassReader originalReader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        Pair<RelativePath, ClassVisitor> pathAndVisitor;
        try {
            pathAndVisitor = transform.apply(classEntry, writer, new ClassData(originalReader, bytes));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        originalReader.accept(pathAndVisitor.right(), ClassReader.EXPAND_FRAMES);

        return writer.toByteArray();
    }
}
