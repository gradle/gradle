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

package org.gradle.util

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder

/**
 * Test utilities for working with {@link ProjectBuilder}.
 */
class ProjectBuilderTestUtil {

    /**
     * Creates a root project in the directory provided by the {@link TestDirectoryProvider}.
     * <p>
     * Prefer {@link AbstractProjectBuilderSpec}.
     */
    static ProjectInternal createRootProject(TestDirectoryProvider testDirectoryProvider) {
        createRootProject(testDirectoryProvider.testDirectory)
    }

    /**
     * Creates a root project in the specified directory.
     * <p>
     * Prefer {@link AbstractProjectBuilderSpec}.
     */
    static ProjectInternal createRootProject(File rootDir, File userHomeDir = null) {
        def builder = ProjectBuilder
            .builder()
            .withProjectDir(rootDir)
            .withName("test-project")
        if (userHomeDir != null) {
            builder.withGradleUserHomeDir(userHomeDir)
        }
        return builder.build()
    }

    static ProjectInternal createChildProject(ProjectInternal parent, String name, File projectDir = null) {
        return ProjectBuilder
            .builder()
            .withName(name)
            .withParent(parent)
            .withProjectDir(projectDir)
            .build()
    }
}
