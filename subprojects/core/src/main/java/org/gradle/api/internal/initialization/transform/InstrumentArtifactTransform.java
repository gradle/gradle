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

package org.gradle.api.internal.initialization.transform;

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
import org.gradle.api.internal.file.archive.impl.FileZipInput;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.Pair;
import org.gradle.internal.classpath.ClassData;
import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.classpath.InstrumentingTransformer;
import org.gradle.internal.classpath.types.InstrumentingTypeRegistry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import static org.gradle.api.internal.initialization.transform.InstrumentArtifactTransform.InstrumentArtifactTransformParameters;

public abstract class InstrumentArtifactTransform implements TransformAction<InstrumentArtifactTransformParameters> {

    private static final int BUFFER_SIZE = 8192;

    public interface InstrumentArtifactTransformParameters extends TransformParameters {
        @InputFiles
        ConfigurableFileCollection getClassHierarchy();
    }

    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInput();

    private File getInputAsFile() {
        return getInput().get().getAsFile();
    }

    @Override
    public void transform(TransformOutputs outputs) {
        File outputFile = outputs.file(getInput().get().getAsFile().getName() + "-instrumented.jar");

        InstrumentingTransformer transformer = new InstrumentingTransformer();
        File jarFile = getInputAsFile();
        try (ZipArchiveOutputStream outputStream = new ZipArchiveOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile.toPath()), BUFFER_SIZE))) {
            outputStream.setLevel(0);
            try (ZipInput entries = FileZipInput.create(jarFile)) {
                for (ZipEntry entry : entries) {
                    if (entry.isDirectory()) {
                        continue;
                    } else if (!entry.getName().endsWith(".class")) {
                        ClasspathWalker.ZipClasspathEntry classEntry = new ClasspathWalker.ZipClasspathEntry(entry);
                        new ClasspathBuilder.ZipEntryBuilder(outputStream).put(classEntry.getPath().getPathString(), entry.getContent(), classEntry.getCompressionMethod());
                        continue;
                    }
                    ClasspathWalker.ZipClasspathEntry classEntry = new ClasspathWalker.ZipClasspathEntry(entry);
                    ClassReader reader = new ClassReader(classEntry.getContent());
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    Pair<RelativePath, ClassVisitor> chain = transformer.apply(classEntry, classWriter, new ClassData(reader, InstrumentingTypeRegistry.EMPTY));
                    reader.accept(chain.right, 0);
                    byte[] bytes = classWriter.toByteArray();
                    new ClasspathBuilder.ZipEntryBuilder(outputStream).put(chain.left.getPathString(), bytes, classEntry.getCompressionMethod());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
