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

package org.gradle.internal.classpath.transforms;

import org.gradle.api.file.RelativePath;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Pair;
import org.gradle.internal.classpath.ClassData;
import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.file.FileException;
import org.gradle.util.internal.JarUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * Base class for the transformations. Note that the order in which entries are visited is not defined.
 */
class BaseClasspathElementTransform implements ClasspathElementTransform {

    private static final Logger LOGGER = Logging.getLogger(BaseClasspathElementTransform.class);

    protected final File source;
    private final ClasspathBuilder classpathBuilder;
    private final ClasspathWalker classpathWalker;
    private final ClassTransform transform;

    BaseClasspathElementTransform(
        File source,
        ClasspathBuilder classpathBuilder,
        ClasspathWalker classpathWalker,
        ClassTransform transform
    ) {
        this.source = source;
        this.classpathBuilder = classpathBuilder;
        this.classpathWalker = classpathWalker;
        this.transform = transform;
    }

    @Override
    public final void transform(File destination) {
        resultBuilder().accept(destination, builder -> {
            try {
                visitEntries(builder);
            } catch (FileException e) {
                // Badly formed archive, so discard the contents and produce an empty JAR
                LOGGER.debug("Malformed archive '{}'. Discarding contents.", source.getName(), e);
            }
        });
    }

    private BiConsumer<File, ClasspathBuilder.Action> resultBuilder() {
        if (source.isDirectory()) {
            return classpathBuilder::directory;
        }
        return classpathBuilder::jar;
    }

    private void visitEntries(ClasspathBuilder.EntryBuilder builder) throws IOException, FileException {
        classpathWalker.visit(source, entry -> {
            visitEntry(builder, entry);
        });
        finishProcessing(builder);
    }

    private void visitEntry(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry entry) throws IOException {
        try {
            if (isClassFile(entry)) {
                processClassFile(builder, entry);
            } else if (isManifest(entry)) {
                processManifest(builder, entry);
            } else {
                processResource(builder, entry);
            }
        } catch (Throwable e) {
            throw new IOException("Failed to process the entry '" + entry.getName() + "' from '" + source + "'", e);
        }
    }

    /**
     * Processes a class file. The type of file is determined solely by name, so it may not be a well-formed class file.
     * Base class implementation applies the {@link ClassTransform} to the code.
     *
     * @param builder the builder for the transformed output
     * @param classEntry the entry to process
     * @throws IOException if reading or writing entry fails
     */
    protected void processClassFile(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry classEntry) throws IOException {
        byte[] content = classEntry.getContent();
        ClassReader reader = new ClassReader(content);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        Pair<RelativePath, ClassVisitor> chain = transform.apply(classEntry, classWriter, new ClassData(reader, content));
        reader.accept(chain.right, 0);
        byte[] bytes = classWriter.toByteArray();
        builder.put(chain.left.getPathString(), bytes, classEntry.getCompressionMethod());
    }

    /**
     * Processes a JAR Manifest. Base class implementation copies the manifest unchanged.
     *
     * @param builder the builder for the transformed output
     * @param manifestEntry the entry to process
     * @throws IOException if reading or writing entry fails
     */
    protected void processManifest(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry manifestEntry) throws IOException {
        processResource(builder, manifestEntry);
    }

    /**
     * Processes a resource entry. Base class implementation copies the resource unchanged.
     *
     * @param builder the builder for the transformed output
     * @param resourceEntry the entry to process
     * @throws IOException if reading or writing entry fails
     */
    protected void processResource(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry resourceEntry) throws IOException {
        builder.put(resourceEntry.getName(), resourceEntry.getContent(), resourceEntry.getCompressionMethod());
    }

    protected void finishProcessing(ClasspathBuilder.EntryBuilder builder) throws IOException {}

    private boolean isClassFile(ClasspathEntryVisitor.Entry entry) {
        return entry.getName().endsWith(".class");
    }

    private boolean isManifest(ClasspathEntryVisitor.Entry entry) {
        return JarUtil.isManifestName(entry.getName());
    }
}
