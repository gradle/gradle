/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform.filters;

import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.DirectorySource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.FileSystemSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

import java.io.File;
import java.nio.file.Path;

/**
 * A JUnit Platform {@link PostDiscoveryFilter} filter that includes or excludes
 * file or directory based tests based on their relative paths to the root directory
 * of the project that contains them.
 */
public final class FilePathFilter implements PostDiscoveryFilter {
    private final TestSelectionMatcher matcher;
    private final File baseFilterDir;

    public FilePathFilter(TestSelectionMatcher matcher, File baseFilterDir) {
        this.matcher = matcher;
        this.baseFilterDir = baseFilterDir;
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        return FilterResult.includedIf(shouldRun(descriptor), () -> "File match", () -> "File mismatch");
    }

    private boolean shouldRun(TestDescriptor descriptor) {
        TestSource testSource = descriptor.getSource().orElseThrow(() -> new IllegalArgumentException("No test source found for " + descriptor));
        if (testSource instanceof FileSource || testSource instanceof DirectorySource) {
            return fileMatch(((FileSystemSource) testSource).getFile());
        }

        return false;
    }

    private boolean fileMatch(File file) {
        Path relativePath = baseFilterDir.toPath().relativize(file.toPath());
        return matcher.matchesPath(relativePath);
    }
}
