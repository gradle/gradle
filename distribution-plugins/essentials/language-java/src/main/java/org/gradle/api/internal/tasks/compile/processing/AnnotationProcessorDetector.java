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
import com.google.common.base.Enums;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType;
import org.gradle.cache.internal.FileContentCache;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.serialize.ListSerializer;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inspects a classpath to find annotation processors contained in it. If several versions of the same annotation processor are found,
 * the first one is returned, mimicking the behavior of {@link java.util.ServiceLoader}.
 */
public class AnnotationProcessorDetector {

    public static final String PROCESSOR_DECLARATION = "META-INF/services/javax.annotation.processing.Processor";
    public static final String INCREMENTAL_PROCESSOR_DECLARATION = "META-INF/gradle/incremental.annotation.processors";

    private final FileContentCache<List<AnnotationProcessorDeclaration>> cache;
    private final Logger logger;
    private final boolean logStackTraces;

    public AnnotationProcessorDetector(FileContentCacheFactory cacheFactory, Logger logger, boolean logStackTraces) {
        this.cache = cacheFactory.newCache("annotation-processors", 20000, new ProcessorServiceLocator(), new ListSerializer<AnnotationProcessorDeclaration>(AnnotationProcessorDeclarationSerializer.INSTANCE));
        this.logger = logger;
        this.logStackTraces = logStackTraces;
    }

    public Map<String, AnnotationProcessorDeclaration> detectProcessors(Iterable<File> processorPath) {
        Map<String, AnnotationProcessorDeclaration> processors = Maps.newLinkedHashMap();
        for (File jarOrClassesDir : processorPath) {
            for (AnnotationProcessorDeclaration declaration : cache.get(jarOrClassesDir)) {
                String className = declaration.getClassName();
                if (!processors.containsKey(className)) {
                    processors.put(className, declaration);
                }
            }
        }
        return processors;
    }

    /*
     * TODO once source compatibility is raised to 1.7, this should be rewritten using the java.nio.FileSystem API,
     * which can deal with jars and folders the same way instead of duplicating code.
     */
    private class ProcessorServiceLocator implements FileContentCacheFactory.Calculator<List<AnnotationProcessorDeclaration>> {

        @Override
        public List<AnnotationProcessorDeclaration> calculate(File file, boolean isRegularFile) {
            if (!isRegularFile) {
                return detectProcessorsInClassesDir(file);
            } else if (FileUtils.hasExtensionIgnoresCase(file.getName(), ".jar")) {
                return detectProcessorsInJar(file);
            }
            return Collections.emptyList();
        }

        private List<AnnotationProcessorDeclaration> detectProcessorsInClassesDir(File classesDir) {
            try {
                List<String> processorClassNames = getProcessorClassNames(classesDir);
                try {
                    Map<String, IncrementalAnnotationProcessorType> processorTypes = getProcessorTypes(classesDir);
                    return toProcessorDeclarations(processorClassNames, processorTypes);
                } catch (Exception e) {
                    logger.warn("Could not read annotation processor declarations from " + classesDir + ". Gradle will assume that all processors in this directory are non-incremental.", logStackTraces ? e : null);
                    return toProcessorDeclarations(processorClassNames, Collections.<String, IncrementalAnnotationProcessorType>emptyMap());
                }
            } catch (Exception e) {
                logger.warn("Could not read annotation processor declarations from " + classesDir + ". Gradle will assume that this directory contains no annotation processors.", logStackTraces ? e : null);
                return Collections.emptyList();
            }
        }

        private List<String> getProcessorClassNames(File classesDir) throws IOException {
            File processorDeclaration = new File(classesDir, PROCESSOR_DECLARATION);
            if (!processorDeclaration.isFile()) {
                return Collections.emptyList();
            }
            return readLines(processorDeclaration);
        }

        private Map<String, IncrementalAnnotationProcessorType> getProcessorTypes(File classesDir) throws IOException {
            File incrementalProcessorDeclaration = new File(classesDir, INCREMENTAL_PROCESSOR_DECLARATION);
            if (!incrementalProcessorDeclaration.isFile()) {
                return Collections.emptyMap();
            }
            List<String> lines = readLines(incrementalProcessorDeclaration);
            return parseIncrementalProcessors(lines);
        }

        private List<String> readLines(File file) throws IOException {
            return Files.asCharSource(file, Charsets.UTF_8).readLines(new MetadataLineProcessor());
        }

        private List<AnnotationProcessorDeclaration> detectProcessorsInJar(File jar) {
            try {
                ZipFile zipFile = new ZipFile(jar);
                try {
                    List<String> processorClassNames = getProcessorClassNames(zipFile);
                    try {
                        Map<String, IncrementalAnnotationProcessorType> processorTypes = getProcessorTypes(zipFile);
                        return toProcessorDeclarations(processorClassNames, processorTypes);
                    } catch (Exception e) {
                        logger.warn("Could not read annotation processor declarations from " + jar + ". Gradle will assume that all processors in this jar are non-incremental.", logStackTraces ? e : null);
                        return toProcessorDeclarations(processorClassNames, Collections.<String, IncrementalAnnotationProcessorType>emptyMap());
                    }
                } finally {
                    zipFile.close();
                }
            } catch (Exception e) {
                logger.warn("Could not read annotation processor declarations from " + jar + ". Gradle will assume that this jar contains no annotation processors.", logStackTraces ? e : null);
                return Collections.emptyList();
            }
        }

        private List<String> getProcessorClassNames(ZipFile zipFile) throws IOException {
            ZipEntry processorDeclaration = zipFile.getEntry(PROCESSOR_DECLARATION);
            if (processorDeclaration == null) {
                return Collections.emptyList();
            }
            return readLines(zipFile, processorDeclaration);
        }

        private Map<String, IncrementalAnnotationProcessorType> getProcessorTypes(ZipFile zipFile) throws IOException {
            ZipEntry incrementalProcessorDeclaration = zipFile.getEntry(INCREMENTAL_PROCESSOR_DECLARATION);
            if (incrementalProcessorDeclaration == null) {
                return Collections.emptyMap();
            }
            List<String> lines = readLines(zipFile, incrementalProcessorDeclaration);
            return parseIncrementalProcessors(lines);
        }

        private List<String> readLines(ZipFile zipFile, ZipEntry zipEntry) throws IOException {
            InputStream in = zipFile.getInputStream(zipEntry);
            try {
                return CharStreams.readLines(new InputStreamReader(in, Charsets.UTF_8), new MetadataLineProcessor());
            } finally {
                in.close();
            }
        }

        private Map<String, IncrementalAnnotationProcessorType> parseIncrementalProcessors(List<String> lines) {
            Map<String, IncrementalAnnotationProcessorType> types = Maps.newHashMap();
            for (String line : lines) {
                List<String> parts = Splitter.on(',').splitToList(line);
                IncrementalAnnotationProcessorType type = parseProcessorType(parts);
                types.put(parts.get(0), type);
            }
            return types;
        }

        private IncrementalAnnotationProcessorType parseProcessorType(List<String> parts) {
            return Enums.getIfPresent(IncrementalAnnotationProcessorType.class, parts.get(1).toUpperCase(Locale.ROOT)).or(IncrementalAnnotationProcessorType.UNKNOWN);
        }

        private List<AnnotationProcessorDeclaration> toProcessorDeclarations(List<String> processorNames, Map<String, IncrementalAnnotationProcessorType> processorTypes) {
            if (processorNames.isEmpty()) {
                return Collections.emptyList();
            }
            ImmutableList.Builder<AnnotationProcessorDeclaration> processors = ImmutableList.builder();
            for (String name : processorNames) {
                IncrementalAnnotationProcessorType type = processorTypes.get(name);
                type = type != null ? type : IncrementalAnnotationProcessorType.UNKNOWN;
                processors.add(new AnnotationProcessorDeclaration(name, type));
            }
            return processors.build();
        }

        private class MetadataLineProcessor implements LineProcessor<List<String>> {
            private List<String> lines = Lists.newArrayList();

            @Override
            public boolean processLine(String line) {
                int commentStart = line.indexOf('#');
                if (commentStart >= 0) {
                    line = line.substring(0, commentStart);
                }
                line = line.trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
                return true;
            }

            @Override
            public List<String> getResult() {
                return lines;
            }
        }
    }
}
