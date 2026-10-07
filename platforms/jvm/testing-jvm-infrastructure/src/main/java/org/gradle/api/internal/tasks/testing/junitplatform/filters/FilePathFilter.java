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
 * A JUnit Platform {@link PostDiscoveryFilter} filter that includes or excludes
 * file or directory based tests based on their relative path to the
 * directory that they were selected from.
 * <p>
 * {@code src/test/definitions/sub/foo.test} has a relative path of {@code sub/foo.test}
 * relative to the {@code directory src/test/definitions}.
 * <p>
 * Only a file under one of those directories has such a relative path. A file-based test that lies
 * under none of them was reached some other way, typically through a class-based entry point such
 * as a JUnit Platform {@code @Suite} selecting feature files of its own, and there is no path to
 * judge it by. Excluding it for failing to match a path it never had would make it unselectable:
 * no pattern could bring it back, not even one naming its own entry point class. So this filter
 * asks for the name first, and hands a file it has no name for to the fallback filter, which
 * matches it by its enclosing class, the way it matches any other test not declared as a method.
 * <p>
 * The result is a two-tier rule. A file-based test under one of the directories is selected by its
 * path relative to that directory; one outside them is selected by the class that encloses it.
 * <p>
 * A file with neither — outside every directory and with no class anywhere above it — is excluded.
 * Nothing names such a test, so handing it to the fallback would include it and leave no pattern
 * able to exclude it again. See {@link #hasEnclosingClass(TestDescriptor)}.
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
        // One lookup decides both whether this filter can judge the file and what it is judged by,
        // so the verdict cannot straddle a change to the file between two separate questions.
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
     * Whether this descriptor, or any ancestor of it, is backed by a class.
     *
     * <p>Only then can the fallback filter render an informed opinion on a file this filter has no
     * name for. Without one, nothing names the test at all: not a path, since it lies under none of
     * the test definition directories, and not a class. The fallback would include it and no
     * pattern could exclude it, leaving a filtered run executing a test that no filter can address,
     * so it is excluded here instead.
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
