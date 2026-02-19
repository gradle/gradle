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

    public TestSelectionMatcher(TestFilterSpec filter, Collection<Path> roots) {
        classTestSelectionMatcher = new ClassTestSelectionMatcher(filter.getIncludedTests(), filter.getExcludedTests(), filter.getIncludedTestsCommandLine());
        fileTestSelectionMatcher = new FileTestSelectionMatcher(classTestSelectionMatcher, roots);
    }

    public FileTestSelectionMatcher getFileTestSelectionMatcher() {
        return fileTestSelectionMatcher;
    }

    public ClassTestSelectionMatcher getClassTestSelectionMatcher() {
        return classTestSelectionMatcher;
    }
}
