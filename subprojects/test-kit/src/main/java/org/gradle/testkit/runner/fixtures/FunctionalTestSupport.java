/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testkit.runner.fixtures;

import org.gradle.api.Incubating;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.fixtures.file.TestFile;

import java.util.List;

/**
 * Provides functional test support for TestKit fixtures.
 *
 * @since 4.2
 * @see JUnit4FunctionalTest
 * @see TestNGFunctionalTest
 * @see SpockFunctionalTest
 * @see FunctionalTestSupportTrait
 */
@Incubating
public interface FunctionalTestSupport {

    /**
     * Initializes fixture. Needs to be called before any other operations.
     */
    void initialize();

    /**
     * Tears down fixture. To be called after fixture is not needed anymore.
     */
    void tearDown();

    /**
     * Returns the {@link org.gradle.testkit.runner.GradleRunner} instance used to execute build.
     *
     * @return The Gradle runner instance
     */
    GradleRunner getGradleRunner();

    /**
     * Executes build for provided arguments and expects it finish successfully.
     *
     * @param arguments Arguments
     * @return Build result
     * @see #succeeds(String...)
     */
    BuildResult succeeds(List<String> arguments);

    /**
     * Executes build for provided arguments and expects it finish successfully.
     *
     * @param arguments Arguments
     * @return Build result
     * @see #succeeds(List)
     */
    BuildResult succeeds(String... arguments);

    /**
     * Executes build for provided arguments and expects it fail.
     *
     * @param arguments Arguments
     * @return Build result
     * @see #fails(String...)
     */
    BuildResult fails(List<String> arguments);

    /**
     * Executes build for provided arguments and expects it fail.
     *
     * @param arguments Arguments
     * @return Build result
     * @see #fails(List)
     */
    BuildResult fails(String... arguments);

    /**
     * Returns the root directory for a test.
     *
     * @return Root test directory
     */
    TestFile getTestDirectory();

    /**
     * Create a new file with the given path.
     *
     * @param path Path
     * @return The created file
     */
    TestFile file(Object... path);

    /**
     * Returns a reference to the build file.
     *
     * @return The build file
     */
    TestFile getBuildFile();

    /**
     * Returns a reference to the settings file.
     *
     * @return The build file
     */
    TestFile getSettingsFile();
}
