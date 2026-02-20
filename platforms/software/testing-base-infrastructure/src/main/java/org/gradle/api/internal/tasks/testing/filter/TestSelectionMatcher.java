/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.filter;


import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

/**
 * This class has two public APIs:
 *
 * <ul>
 * <li>Judge whether a test class might be included. For example, class 'org.gradle.Test' can't
 * be included by pattern 'org.apache.Test'
 * <li>Judge whether a test method is matched exactly.
 * </ul>
 *
 * In both cases, if the pattern starts with an upper-case letter, it will be used to match
 * the simple class name;
 * otherwise, it will be used to match the fully qualified class name.
 */
public class TestSelectionMatcher {

    private final ClassTestSelectionMatcher classTestSelectionMatcher;
    private final FileTestSelectionMatcher fileTestSelectionMatcher;

    public TestSelectionMatcher(TestFilterSpec filter) {
        this(filter, Collections.emptyList());
    }

    /**
     * Create a test matcher.
     * @param filter the include and exclude patterns to use as a filter
     * @param roots the roots to search when matching on file paths
     */
    public TestSelectionMatcher(TestFilterSpec filter, Collection<Path> roots) {
        classTestSelectionMatcher = new ClassTestSelectionMatcher(filter.getIncludedTests(), filter.getExcludedTests(), filter.getIncludedTestsCommandLine());
        fileTestSelectionMatcher = new FileTestSelectionMatcher(classTestSelectionMatcher, roots);
    }

    /**
     * Returns true if the given file matches any given include patterns and is not discarded by any exclude patterns.
     *
     * @see FileTestSelectionMatcher
     */
    public boolean matchesFile(File file) {
        return fileTestSelectionMatcher.matchesFile(file);
    }

    /**
     * Returns true if the given class and method matches any include pattern and is not discarded by any exclude pattern.
     *
     * @see ClassTestSelectionMatcher
     */
    public boolean matchesTest(String className, @Nullable String methodName) {
        return classTestSelectionMatcher.matchesTest(className, methodName);
    }

    /**
     * Returns true if the given fully qualified class name may be included in test execution. This is more optimistic than {@link #matchesTest(String, String)}
     * because some classes may still be excluded later for other reasons.
     *
     * @see ClassTestSelectionMatcher
     */
    public boolean mayIncludeClass(String fullQualifiedClassName) {
        return classTestSelectionMatcher.mayIncludeClass(fullQualifiedClassName);
    }
}
