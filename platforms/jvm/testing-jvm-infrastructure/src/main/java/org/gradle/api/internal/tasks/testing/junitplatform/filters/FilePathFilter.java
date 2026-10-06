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
import org.jspecify.annotations.NullMarked;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.DirectorySource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.FileSystemSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

import java.io.File;

/**
 * A JUnit Platform {@link PostDiscoveryFilter} filter that includes or excludes
 * file or directory based tests based on their relative path to the
 * directory that they were selected from.
 * <p>
 * {@code src/test/definitions/sub/foo.test} has a relative path of {@code sub/foo.test}
 * relative to the {@code directory src/test/definitions}.
 * <p>
 * Only a file under one of those directories has such a relative path. A file-based test that
 * lies under none of them was reached some other way, typically through a class-based entry point
 * such as a JUnit Platform {@code @Suite} selecting feature files of its own, and there is no path
 * to judge it by. Rather than exclude it for failing to match a path it never had, this filter
 * hands it to the fallback filter, which matches it the way it matches any other test that is not
 * declared as a method.
 *
 * @see TestSelectionMatcher#matchesFile(File)
 */
@NullMarked
public final class FilePathFilter implements PostDiscoveryFilter {
    private final TestSelectionMatcher matcher;
    private final PostDiscoveryFilter fallback;

    /**
     * @param matcher the include and exclude patterns, along with the directories to match paths against
     * @param fallback the filter to consult for a file that lies under none of those directories
     */
    public FilePathFilter(TestSelectionMatcher matcher, PostDiscoveryFilter fallback) {
        this.matcher = matcher;
        this.fallback = fallback;
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        File file = getFile(descriptor);
        if (!matcher.canMatchFile(file)) {
            return fallback.apply(descriptor);
        } else {
            return FilterResult.includedIf(matcher.matchesFile(file), () -> "File match", () -> "File mismatch");
        }
    }

    private static File getFile(TestDescriptor descriptor) {
        TestSource testSource = descriptor.getSource().orElseThrow(() -> new IllegalArgumentException("No test source found for " + descriptor));
        if (!(testSource instanceof FileSource) && !(testSource instanceof DirectorySource)) {
            throw new IllegalArgumentException("Test source must be file or directory based, was: " + testSource);
        }
        return ((FileSystemSource) testSource).getFile();
    }
}
