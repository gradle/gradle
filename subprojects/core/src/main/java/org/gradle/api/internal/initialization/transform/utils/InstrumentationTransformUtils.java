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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.gradle.internal.classpath.TransformedClassPath.FileMarker.INSTRUMENTATION_CLASSPATH_MARKER;

public class InstrumentationTransformUtils {

    public enum InstrumentationInputType {
        DEPENDENCY_ANALYSIS_DATA,
        TYPE_HIERARCHY_ANALYSIS_DATA,
        INSTRUMENTATION_MARKER,
        ORIGINAL_ARTIFACT
    }

    public static final String ANALYSIS_OUTPUT_DIR = "analysis";
    public static final String MERGE_OUTPUT_DIR = "merge";
    public static final String TYPE_HIERARCHY_ANALYSIS_FILE_NAME = "instrumentation-hierarchy.bin";
    public static final String DEPENDENCY_ANALYSIS_FILE_NAME = "instrumentation-dependencies.bin";

    public static InstrumentationInputType getInputType(File input) {
        if (isInstrumentationMarkerFile(input)) {
            return InstrumentationInputType.INSTRUMENTATION_MARKER;
        } else if (isDependencyAnalysisFile(input)) {
            return InstrumentationInputType.DEPENDENCY_ANALYSIS_DATA;
        } else if (isTypeHierarchyAnalysisFile(input)) {
            return InstrumentationInputType.TYPE_HIERARCHY_ANALYSIS_DATA;
        } else {
            return InstrumentationInputType.ORIGINAL_ARTIFACT;
        }
    }

    public static boolean isDependencyAnalysisFile(File input) {
        return input.getName().equals(DEPENDENCY_ANALYSIS_FILE_NAME);
    }

    public static boolean isTypeHierarchyAnalysisFile(File input) {
        return input.getName().equals(TYPE_HIERARCHY_ANALYSIS_FILE_NAME);
    }

    public static boolean isInstrumentationMarkerFile(File input) {
        return input.getName().equals(INSTRUMENTATION_CLASSPATH_MARKER.getFileName());
    }

    public static void outputOriginalArtifact(TransformOutputs outputs, File originalArtifact) {
        if (originalArtifact.isDirectory()) {
            outputs.dir(originalArtifact);
        } else {
            outputs.file(originalArtifact);
        }
    }

    public static void createNewFile(File file) {
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void createInstrumentationClasspathMarker(TransformOutputs outputs) {
        createNewFile(outputs.file(INSTRUMENTATION_CLASSPATH_MARKER.getFileName()));
    }
}
