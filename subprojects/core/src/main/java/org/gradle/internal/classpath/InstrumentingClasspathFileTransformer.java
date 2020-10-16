/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RelativePath;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.internal.Pair;
import org.gradle.internal.file.FileException;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

class InstrumentingClasspathFileTransformer implements ClasspathFileTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentingClasspathFileTransformer.class);

    private final CopyingClasspathFileTransformer copyingClasspathFileTransformer;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;
    private final CachedClasspathTransformer.Transform transform;
    private final HashCode configHash;

    public InstrumentingClasspathFileTransformer(GlobalCacheLocations globalCacheLocations, ClasspathWalker classpathWalker, ClasspathBuilder classpathBuilder, CachedClasspathTransformer.Transform transform) {
        this.copyingClasspathFileTransformer = new CopyingClasspathFileTransformer(globalCacheLocations);
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;
        this.transform = transform;
        Hasher hasher = Hashing.defaultFunction().newHasher();
        transform.applyConfigurationTo(hasher);
        configHash = hasher.hash();
    }

    @Override
    public File transform(File source, CompleteFileSystemLocationSnapshot sourceSnapshot, File cacheDir) {
        String name = sourceSnapshot.getType() == FileType.Directory ? source.getName() + ".jar" : source.getName();
        Hasher hasher = Hashing.defaultFunction().newHasher();
        hasher.putHash(configHash);
        // TODO - apply runtime classpath normalization?
        hasher.putHash(sourceSnapshot.getHash());
        HashCode fileHash = hasher.hash();
        File transformed = new File(cacheDir, fileHash.toString() + '/' + name);
        if (!transformed.isFile()) {
            transformed = transform(source, transformed, sourceSnapshot, cacheDir);
        }
        return transformed;
    }

    private File transform(File source, File dest, CompleteFileSystemLocationSnapshot sourceSnapshot, File cacheDir) {
        SignedJarDetection signedJarDetection = new SignedJarDetection();
        try {
            classpathWalker.visit(source, signedJarDetection);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (FileException e) {
            LOGGER.debug("Malformed archive '{}'. Discarding contents.", source.getName(), e);
            classpathBuilder.jar(dest, builder -> {
                // Badly formed archive, so discard the contents and produce an empty JAR
            });
            return dest;
        }
        if (signedJarDetection.hasSignature) {
            LOGGER.debug("Signed archive '{}'. Skipping instrumentation.", source.getName());
            return copyingClasspathFileTransformer.transform(source, sourceSnapshot, cacheDir);
        } else {
            instrument(source, dest);
            return dest;
        }
    }

    private void instrument(File source, File dest) {
        classpathBuilder.jar(dest, builder -> {
            try {
                visitEntries(source, builder);
            } catch (FileException e) {
                // Badly formed archive, so discard the contents and produce an empty JAR
                LOGGER.debug("Malformed archive '{}'. Discarding contents.", source.getName(), e);
            }
        });
    }

    private void visitEntries(File source, ClasspathBuilder.EntryBuilder builder) throws IOException, FileException {
        classpathWalker.visit(source, entry -> {
            if (entry.getName().endsWith(".class")) {
                ClassReader reader = new ClassReader(entry.getContent());
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                Pair<RelativePath, ClassVisitor> chain = transform.apply(entry, classWriter);
                reader.accept(chain.right, 0);
                byte[] bytes = classWriter.toByteArray();
                builder.put(chain.left.getPathString(), bytes);
            } else {
                builder.put(entry.getName(), entry.getContent());
            }
        });
    }

    private static class SignedJarDetection implements ClasspathEntryVisitor {

        boolean hasSignature = false;

        @Override
        public void visit(Entry entry) {
            if (entry.getName().startsWith("META-INF/") && entry.getName().endsWith(".SF")) {
                hasSignature = true;
            }
        }
    }
}
