/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ConstantsMappingFileAccessor {
    private static final int BUFFER_SIZE = 65536;

    public static Map<String, Collection<String>> readConstantsClassesMappingFile(File mappingFile) {
        Map<String, Collection<String>> sourceClassesMapping = new HashMap<>();
        if (!mappingFile.isFile()) {
            throw new GradleException("Constants class mapping is not a file as expected. Path to file: " + mappingFile.getAbsolutePath());
        }

        try {
            Files.asCharSource(mappingFile, Charsets.UTF_8).readLines(new LineProcessor<Void>() {
                private String currentFile;
                @Override
                public boolean processLine(String line) {
                    if (line.startsWith(" ")) {
                        sourceClassesMapping.get(currentFile).add(line.substring(1));
                    } else {
                        currentFile = line;
                        sourceClassesMapping.computeIfAbsent(line, k -> new ArrayList<>());
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

    public static void writeConstantsClassesMappingFile(File mappingFile, Map<String, Collection<String>> mapping) {
        try (Writer wrt = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mappingFile, false), StandardCharsets.UTF_8), BUFFER_SIZE)) {
            for (Map.Entry<String, Collection<String>> entry : mapping.entrySet()) {
                wrt.write(entry.getKey() + "\n");
                for (String constantOrigin : entry.getValue()) {
                    wrt.write(" " + constantOrigin + "\n");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
