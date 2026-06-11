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

package org.gradle.internal.classpath.transforms;

import org.gradle.api.file.RelativePath;
import org.gradle.internal.Pair;
import org.gradle.internal.classpath.ClassData;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;

/**
 * Shared ASM plumbing that runs a {@link ClassTransform} against a single class entry
 * and returns the resulting path and bytes.
 */
public final class ClassTransforms {

    private ClassTransforms() {}

    public static Pair<RelativePath, byte[]> apply(ClassTransform transform, ClasspathEntryVisitor.Entry classEntry) throws IOException {
        byte[] content = classEntry.getContent();
        ClassReader reader = new ClassReader(content);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        Pair<RelativePath, ClassVisitor> chain = transform.apply(classEntry, writer, new ClassData(reader, content));
        reader.accept(chain.right, 0);
        return Pair.of(chain.left, writer.toByteArray());
    }

    public static byte[] applyToBytes(ClassTransform transform, String className, byte[] content) {
        try {
            return apply(transform, new SyntheticClassEntry(className, content)).right;
        } catch (IOException e) {
            throw new AssertionError("SyntheticClassEntry.getContent() does not perform I/O", e);
        }
    }

    private static final class SyntheticClassEntry implements ClasspathEntryVisitor.Entry {

        private final String className;
        private final byte[] content;

        SyntheticClassEntry(String className, byte[] content) {
            this.className = className;
            this.content = content;
        }

        @Override
        public String getName() {
            return className + ".class";
        }

        @Override
        public RelativePath getPath() {
            return RelativePath.parse(true, className + ".class");
        }

        @Override
        public CompressionMethod getCompressionMethod() {
            return CompressionMethod.UNDEFINED;
        }

        @Override
        public byte[] getContent() {
            return content;
        }
    }
}
