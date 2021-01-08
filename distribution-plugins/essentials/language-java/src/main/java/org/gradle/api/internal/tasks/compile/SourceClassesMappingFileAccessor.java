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

package org.gradle.api.internal.tasks.compile;

import com.google.common.base.Charsets;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Read and write a file recording source file to class names mapping.
 *
 * The file format is:
 *
 * relative/path/to/source/root/MyGroovyClass.groovy
 * org.gradle.MyGroovyClass
 * org.gradle.MyGroovyClass$1
 * org.gradle.MyGroovyClass$Inner
 */
public class SourceClassesMappingFileAccessor {
    private static final int BUFFER_SIZE = 65536;

    public static Multimap<String, String> readSourceClassesMappingFile(File mappingFile) {
        Multimap<String, String> sourceClassesMapping = MultimapBuilder.SetMultimapBuilder
            .hashKeys()
            .hashSetValues()
            .build();
        if (!mappingFile.isFile()) {
            return sourceClassesMapping;
        }

        try {
            Files.asCharSource(mappingFile, Charsets.UTF_8).readLines(new LineProcessor<Void>() {
                private String currentFile;

                @Override
                public boolean processLine(String line) throws IOException {
                    if (line.startsWith(" ")) {
                        sourceClassesMapping.put(currentFile, line.substring(1));
                    } else {
                        currentFile = line;
                    }

                    return true;
                }

                @Override
                public Void getResult() {
                    return null;
                }
            });
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        return sourceClassesMapping;
    }

    public static void writeSourceClassesMappingFile(File mappingFile, Map<String, Collection<String>> mapping) {
        try (Writer wrt = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mappingFile, false), StandardCharsets.UTF_8), BUFFER_SIZE)) {
            for (Map.Entry<String, Collection<String>> entry : mapping.entrySet()) {
                wrt.write(entry.getKey() + "\n");
                for (String className : entry.getValue()) {
                    wrt.write(" " + className + "\n");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeSourceClassesMappingFile(File mappingFile, Multimap<String, String> mapping) {
        writeSourceClassesMappingFile(mappingFile, mapping.asMap());
    }

    public static void mergeIncrementalMappingsIntoOldMappings(File sourceClassesMappingFile,
                                                               FileCollection stableSources,
                                                               InputChanges inputChanges,
                                                               Multimap<String, String> oldMappings) {
        Multimap<String, String> mappingsDuringIncrementalCompilation = readSourceClassesMappingFile(sourceClassesMappingFile);

        StreamSupport.stream(inputChanges.getFileChanges(stableSources).spliterator(), false)
            .filter(fileChange -> fileChange.getChangeType() == ChangeType.REMOVED)
            .map(FileChange::getNormalizedPath)
            .forEach(oldMappings::removeAll);
        mappingsDuringIncrementalCompilation.keySet().forEach(oldMappings::removeAll);

        oldMappings.putAll(mappingsDuringIncrementalCompilation);

        writeSourceClassesMappingFile(sourceClassesMappingFile, oldMappings);
    }
}
