/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.processing;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.internal.FileContentCache;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.file.FileType;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

/**
 * Inspects a classpath to find annotation processors contained in it.
 */
public class AnnotationProcessorDetector {
    private final FileContentCache<List<AnnotationProcessorDeclaration>> cache;

    public AnnotationProcessorDetector(FileContentCacheFactory cacheFactory) {
        cache = cacheFactory.newCache("annotation-processors", 20000, new ProcessorServiceLocator(), new ListSerializer<AnnotationProcessorDeclaration>(new AnnotationProcessorDeclarationSerializer()));
    }

    public List<AnnotationProcessorDeclaration> detectProcessors(Iterable<File> processorPath) {
        List<AnnotationProcessorDeclaration> processors = Lists.newArrayList();
        for (File jarOrClassesDir : processorPath) {
            processors.addAll(cache.get(jarOrClassesDir));
        }
        return processors;
    }

    private static class ProcessorServiceLocator implements FileContentCacheFactory.Calculator<List<AnnotationProcessorDeclaration>> {
        @Override
        public List<AnnotationProcessorDeclaration> calculate(File file, FileType fileType) {
            if (fileType == FileType.Directory) {
                return detectProcessorsInClassesDir(file);
            }
            if (fileType == FileType.RegularFile && FileUtils.hasExtensionIgnoresCase(file.getName(), ".jar")) {
                return detectProcessorsInJar(file);
            }
            return Collections.emptyList();
        }

        private List<AnnotationProcessorDeclaration> detectProcessorsInClassesDir(File classesDir) {
            File processorDeclaration = new File(classesDir, "META-INF/services/javax.annotation.processing.Processor");
            if (!processorDeclaration.isFile()) {
                return Collections.emptyList();
            }
            List<String> processorNames = readLines(processorDeclaration);
            return toProcessorDeclarations(processorNames);
        }

        private List<String> readLines(File file) {
            try {
                return Files.readLines(file, Charsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private List<AnnotationProcessorDeclaration> detectProcessorsInJar(File jar) {
            try {
                ZipFile zipFile = new ZipFile(jar);
                try {
                    return detectProcessorsInZipFile(zipFile);
                } finally {
                    zipFile.close();
                }
            } catch (IOException e) {
                DeprecationLogger.nagUserWith("Malformed jar [" + jar.getName() + "] found on compile classpath. Gradle 5.0 will no longer allow malformed jars on the compile classpath.");
                return Collections.emptyList();
            }
        }

        private List<AnnotationProcessorDeclaration> detectProcessorsInZipFile(ZipFile zipFile) throws IOException {
            ZipEntry processorDeclaration = zipFile.getEntry("META-INF/services/javax.annotation.processing.Processor");
            if (processorDeclaration == null) {
                return Collections.emptyList();
            }
            List<String> processorNames = readLines(zipFile, processorDeclaration);
            return toProcessorDeclarations(processorNames);
        }

        private List<String> readLines(ZipFile zipFile, ZipEntry zipEntry) throws IOException {
            InputStream in = zipFile.getInputStream(zipEntry);
            try {
                return CharStreams.readLines(new InputStreamReader(in, Charsets.UTF_8));
            } finally {
                in.close();
            }
        }

        private List<AnnotationProcessorDeclaration> toProcessorDeclarations(List<String> processorNames) {
            if (processorNames.isEmpty()) {
                return Collections.emptyList();
            }
            ImmutableList.Builder<AnnotationProcessorDeclaration> processors = ImmutableList.builder();
            for (String name : processorNames) {
                processors.add(new AnnotationProcessorDeclaration(name));
            }
            return processors.build();
        }
    }
}
