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
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.DirectorySource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.FileSystemSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

import java.io.File;
import java.util.Optional;

/**
 * A JUnit Platform {@link PostDiscoveryFilter} that matches file and directory based tests by their
 * path relative to the task's test definition directories.
 * <p>
 * With {@code src/test/definitions} configured, {@code src/test/definitions/sub/foo.test} is matched
 * as {@code sub.foo}.
 * <p>
 * A file under none of those directories has no such path. It was reached some other way, typically
 * a JUnit Platform {@code @Suite} selecting files of its own, and is handed to the fallback filter
 * to be matched by its enclosing class. With no enclosing class either, no pattern can name it and
 * it is excluded.
 * <p>
 * The result is a two-tier rule. A file-based test under one of the directories is selected by its
 * path relative to that directory; one outside them is selected by the class that encloses it.
 *
 * @see TestSelectionMatcher#nameForFile(File)
 */
@NullMarked
public final class FilePathFilter implements PostDiscoveryFilter {
    private final TestSelectionMatcher matcher;
    private final PostDiscoveryFilter fallback;

    /**
     * @param matcher the include and exclude patterns, along with the directories to match paths against
     * @param fallback the filter to consult for a file that has no name relative to those directories
     */
    public FilePathFilter(TestSelectionMatcher matcher, PostDiscoveryFilter fallback) {
        this.matcher = matcher;
        this.fallback = fallback;
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        Optional<String> name = matcher.nameForFile(getFile(descriptor));
        if (name.isPresent()) {
            return FilterResult.includedIf(matcher.matchesTest(name.get(), ""), () -> "File match", () -> "File mismatch");
        } else if (hasEnclosingClass(descriptor)) {
            return fallback.apply(descriptor);
        } else {
            return FilterResult.excluded("Neither a file path nor an enclosing class to match against");
        }
    }

    /**
     * Whether this descriptor, or any ancestor of it, is backed by a class, and so can be matched by
     * class name.
     */
    private static boolean hasEnclosingClass(TestDescriptor descriptor) {
        for (Optional<TestDescriptor> current = Optional.of(descriptor); current.isPresent(); current = current.get().getParent()) {
            if (current.get().getSource().filter(ClassSource.class::isInstance).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static File getFile(TestDescriptor descriptor) {
        TestSource testSource = descriptor.getSource().orElseThrow(() -> new IllegalArgumentException("No test source found for " + descriptor));
        if (!(testSource instanceof FileSource) && !(testSource instanceof DirectorySource)) {
            throw new IllegalArgumentException("Test source must be file or directory based, was: " + testSource);
        }
        return ((FileSystemSource) testSource).getFile();
    }
}
