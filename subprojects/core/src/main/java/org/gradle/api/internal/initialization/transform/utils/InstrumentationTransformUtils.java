/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization.transform.utils;

import com.google.common.base.Splitter;
import org.gradle.api.artifacts.transform.TransformOutputs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME;

public class InstrumentationTransformUtils {

    public static final String FILE_NAME_PROPERTY_NAME = "-name-";
    public static final String FILE_HASH_PROPERTY_NAME = "-hash-";
    public static final String ANALYSIS_OUTPUT_DIR = "analysis";
    public static final String FILE_MISSING_HASH = "<missing-hash>";
    public static final String METADATA_FILE_NAME = "metadata.properties";
    public static final String DEPENDENCIES_FILE_NAME = "dependencies.txt";
    public static final String SUPER_TYPES_FILE_NAME = "super-types.properties";
    public static final String DEPENDENCIES_SUPER_TYPES_FILE_NAME = "dependencies-super-types.properties";

    public static boolean createNewFile(File file) {
        try {
            return file.createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void createInstrumentationClasspathMarker(TransformOutputs outputs) {
        try {
            // Mark the folder, so we know that this is a folder with super types files.
            // The only use case right now currently is, that we do not delete folders with such file for performance testing.
            outputs.file(INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME).createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BufferedWriter newBufferedUtf8Writer(File file) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8));
    }

    /**
     * Returns super types for each class in the given properties file.
     */
    public static Map<String, Set<String>> readSuperTypes(File properties) {
        Map<String, Set<String>> result = new HashMap<>();
        try (Stream<String> stream = Files.lines(properties.toPath())) {
            stream.forEach(line -> {
                String[] splitted = line.split("=");
                Set<String> superTypes = Splitter.on(",")
                    .splitToStream(splitted[1])
                    .collect(Collectors.toSet());
                result.put(splitted[0], superTypes);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }
}
