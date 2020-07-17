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

import org.gradle.api.file.RelativePath;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

class InstrumentingClasspathFileTransformer implements ClasspathFileTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentingClasspathFileTransformer.class);
    private static final Attributes.Name DIGEST_ATTRIBUTE = new Attributes.Name("SHA1-Digest");

    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;
    private final CachedClasspathTransformer.Transform transform;
    private final HashCode configHash;

    public InstrumentingClasspathFileTransformer(ClasspathWalker classpathWalker, ClasspathBuilder classpathBuilder, CachedClasspathTransformer.Transform transform) {
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
            transform(source, transformed);
        }
        return transformed;
    }

    private void transform(File source, File dest) {
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
            } else if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                // Remove the signature from the manifest, as the classes may have been instrumented
                Manifest manifest = new Manifest(new ByteArrayInputStream(entry.getContent()));
                manifest.getMainAttributes().remove(Attributes.Name.SIGNATURE_VERSION);
                Iterator<Map.Entry<String, Attributes>> entries = manifest.getEntries().entrySet().iterator();
                while (entries.hasNext()) {
                    Map.Entry<String, Attributes> manifestEntry = entries.next();
                    Attributes attributes = manifestEntry.getValue();
                    attributes.remove(DIGEST_ATTRIBUTE);
                    if (attributes.isEmpty()) {
                        entries.remove();
                    }
                }
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                manifest.write(outputStream);
                builder.put(entry.getName(), outputStream.toByteArray());
            } else if (!entry.getName().startsWith("META-INF/") || !entry.getName().endsWith(".SF")) {
                // Discard signature files, as the classes may have been instrumented
                // Else, copy resource
                builder.put(entry.getName(), entry.getContent());
            }
        });
    }
}
