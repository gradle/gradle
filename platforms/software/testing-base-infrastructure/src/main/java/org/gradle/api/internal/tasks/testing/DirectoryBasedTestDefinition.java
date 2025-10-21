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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * A test definition which denotes a directory containing non-class-based tests.
 */
@NullMarked
public final class DirectoryBasedTestDefinition implements TestDefinition {
    private final File resourceFile;

    public DirectoryBasedTestDefinition(File resourceFile) {
        this.resourceFile = resourceFile;
    }

    public File getTestDefintionFile() {
        return resourceFile;
    }

    /**
     * {@inheritDoc}
     * @implNote Returns the absolute path of the directory.
     */
    @Override
    public String getId() {
        return resourceFile.getAbsolutePath(); // TODO: Relative path here too
    }

    @Override
    public String getDisplayName() {
        // TODO: Use the relative path from the build's root - make field a RelativeFile?
        return "tests in directory '" + resourceFile.getAbsolutePath() + "'";
    }

    @Override
    public boolean matches(TestSelectionMatcher matcher) {
        // TODO: Implement filtering
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DirectoryBasedTestDefinition that = (DirectoryBasedTestDefinition) o;

        return resourceFile.equals(that.resourceFile);
    }

    @Override
    public int hashCode() {
        return resourceFile.hashCode();
    }

    @Override
    public String toString() {
        return "DirectoryBasedTestDefinition(" + getId() + ')';
    }
}
