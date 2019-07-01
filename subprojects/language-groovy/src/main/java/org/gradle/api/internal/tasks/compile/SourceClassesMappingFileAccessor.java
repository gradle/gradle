/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Read and write a file recording Groovy source file to Groovy classes mapping.
 *
 * The file format is:
 *
 * file:///my/path/to/groovy/source/MyGroovyClass.groovy
 *  org.gradle.MyGroovyClass
 *  org.gradle.MyGroovyClass$1
 *  org.gradle.MyGroovyClass$Inner
 */
public class SourceClassesMappingFileAccessor {
    public static Multimap<File, String> readSourceClassesMappingFile(File mappingFile) {
        Multimap<File, String> sourceClassesMapping = MultimapBuilder.SetMultimapBuilder
            .hashKeys()
            .hashSetValues()
            .build();
        if (!mappingFile.isFile()) {
            return sourceClassesMapping;
        }

        try {
            Files.asCharSource(mappingFile, Charsets.UTF_8).readLines(new LineProcessor<Void>() {
                private File currentFile;

                @Override
                public boolean processLine(String line) throws IOException {
                    if (line.startsWith(" ")) {
                        sourceClassesMapping.put(currentFile, line.substring(1));
                    } else {
                        currentFile = new File(line);
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

    public static void writeSourceClassesMappingFile(File mappingFile, Multimap<File, String> mapping) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<File, Collection<String>> entry : mapping.asMap().entrySet()) {
            sb.append(entry.getKey().toString()).append("\n");
            for (String className : entry.getValue()) {
                sb.append(" ").append(className).append("\n");
            }
        }

        GFileUtils.writeFile(sb.toString(), mappingFile);
    }
}
