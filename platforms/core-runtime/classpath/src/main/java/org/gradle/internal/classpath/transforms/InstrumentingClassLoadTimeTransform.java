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
import org.gradle.internal.classloader.ProtectionDomains;
import org.gradle.internal.classpath.ClassData;
import org.gradle.internal.classpath.ClassLoadTimeTransform;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.hash.Hasher;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;

import java.io.File;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runs {@link InstrumentingClassTransform} at class-load time against bytes supplied
 * by the JVM, so that Gradle's instrumentation composes with any third-party
 * {@link java.lang.instrument.ClassFileTransformer} that ran earlier.
 * <p>
 * The transform applied depends on the classpath entry the class originates from, matching
 * the ahead-of-time pipeline that produced the pre-instrumented "double" of that entry.
 * Classes whose code source cannot be resolved to a known classpath entry are left untouched,
 * matching the ahead-of-time behavior where such entries are loaded without substitution.
 * Property-upgrade reporting on project dependencies is not reproduced here.
 */
public final class InstrumentingClassLoadTimeTransform implements ClassLoadTimeTransform {
    private static final ClassTransform NO_TRANSFORM_SENTINEL = new ClassTransform() {
        @Override
        public void applyConfigurationTo(Hasher hasher) {
            throw new UnsupportedOperationException("Cannot apply empty transform");
        }

        @Override
        public Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor, ClassData classData) throws IOException {
            throw new UnsupportedOperationException("Cannot apply empty transform");
        }
    };

    private final Map<File, ClassTransform> transformsByOrigin;
    // The classloader reuses one ProtectionDomain per classpath entry, while this transform runs for
    // every class it loads. Resolving the code source to an entry canonicalizes the file, which hits
    // the filesystem, so cache the result per domain like TransformReplacer does.
    // Empty transforms are represented as NO_TRANSFORM_SENTINEL, so we don't look up their transforms over and over.
    private final ConcurrentMap<ProtectionDomain, ClassTransform> transformsByDomain = new ConcurrentHashMap<>();

    /**
     * @param transformsByOrigin the transform to apply to classes of each original classpath entry
     */
    public InstrumentingClassLoadTimeTransform(Map<File, ClassTransform> transformsByOrigin) {
        this.transformsByOrigin = normalizeKeys(transformsByOrigin);
    }

    @Override
    public byte[] transform(@Nullable ProtectionDomain protectionDomain, String className, byte[] classfileBuffer) {
        ClassTransform transform = protectionDomain != null ? transformFor(protectionDomain) : null;
        if (transform == null) {
            return classfileBuffer;
        }
        return ClassTransforms.applyToBytes(transform, className, classfileBuffer);
    }

    @Nullable
    private ClassTransform transformFor(ProtectionDomain protectionDomain) {
        ClassTransform transform = transformsByDomain.computeIfAbsent(protectionDomain, this::lookUpTransform);
        return transform != NO_TRANSFORM_SENTINEL ? transform : null;
    }

    private ClassTransform lookUpTransform(ProtectionDomain protectionDomain) {
        File codeSourceFile = ProtectionDomains.codeSourceFileOf(protectionDomain);
        if (codeSourceFile == null) {
            return NO_TRANSFORM_SENTINEL;
        }
        ClassTransform transform = transformsByOrigin.get(normalize(codeSourceFile));
        return transform != null ? transform : NO_TRANSFORM_SENTINEL;
    }

    private static Map<File, ClassTransform> normalizeKeys(Map<File, ClassTransform> transformsByOrigin) {
        Map<File, ClassTransform> result = new HashMap<>(transformsByOrigin.size());
        for (Map.Entry<File, ClassTransform> entry : transformsByOrigin.entrySet()) {
            result.put(normalize(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static File normalize(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }
}
