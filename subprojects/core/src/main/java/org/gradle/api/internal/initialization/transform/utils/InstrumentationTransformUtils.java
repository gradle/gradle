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

import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.util.internal.GFileUtils;

import java.io.File;

import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME;

public class InstrumentationTransformUtils {

    public static final String ANALYSIS_OUTPUT_DIR = "analysis";
    public static final String MERGE_OUTPUT_DIR = "merge";
    public static final String METADATA_FILE_NAME = "metadata.bin";
    public static final String DEPENDENCIES_FILE_NAME = "dependencies.bin";
    public static final String SUPER_TYPES_FILE_NAME = "super-types.bin";
    public static final String DEPENDENCIES_SUPER_TYPES_FILE_NAME = "dependencies-super-types.bin";

    public static void createInstrumentationClasspathMarker(File outputDir) {
        GFileUtils.touch(new File(outputDir, INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME));
    }

    public static void createInstrumentationClasspathMarker(TransformOutputs outputs) {
        GFileUtils.touch(outputs.file(INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME));
    }

    public static boolean isAnalysisMetadataDir(File input) {
        return input.isDirectory() && new File(input, INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME).exists();
    }
}
