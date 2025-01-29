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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.NonNullApi;
import org.gradle.util.internal.RelativePathUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Relativizes paths relative to a set of source directories in order to create a
 * platform-independent mapping from source file to class file.
 */
@NonNullApi
public class CompilationSourceDirs {

    private final List<File> sourceRoots;

    public CompilationSourceDirs(JavaCompileSpec spec) {
        this.sourceRoots = new ArrayList<>(spec.getSourceRoots());
        File headerOutputDirectory = spec.getCompileOptions().getHeaderOutputDirectory();
        if (headerOutputDirectory != null) {
            sourceRoots.add(headerOutputDirectory);
        }
        File generatedSourcesDirectory = spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory();
        if (generatedSourcesDirectory != null) {
            sourceRoots.add(generatedSourcesDirectory);
        }
    }

    @VisibleForTesting
    CompilationSourceDirs(List<File> sourceRoots) {
        this.sourceRoots = sourceRoots;
    }

    /**
     * Calculate the relative path to the source root.
     */
    public Optional<String> relativize(File sourceFile) {
        return sourceRoots.stream()
            .filter(sourceDir -> sourceFile.getAbsolutePath().startsWith(sourceDir.getAbsolutePath()))
            .map(sourceDir -> RelativePathUtil.relativePath(sourceDir, sourceFile))
            .filter(relativePath -> !relativePath.startsWith(".."))
            .findFirst();
    }

}
