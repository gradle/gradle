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

package org.gradle.testkit.runner.fixtures

import groovy.transform.CompileStatic
import org.gradle.api.Incubating
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.fixtures.file.TestFile
import org.gradle.testkit.runner.internal.fixtures.DefaultFunctionalTestSupport
import org.gradle.testkit.runner.internal.file.JUnitTemporaryDirectoryProvider
import org.junit.After
import org.junit.Before

/**
 * Test fixture trait for functional testing with TestKit for Groovy-based tests.
 *
 * @since 4.2
 */
@Incubating
@CompileStatic
trait FunctionalTestSupportTrait implements FunctionalTestSupport {

    private final FunctionalTestSupport delegate = new DefaultFunctionalTestSupport(new JUnitTemporaryDirectoryProvider())

    /**
     * {@inheritDoc}
     */
    @Before
    @Override
    void initialize() {
        delegate.initialize()
    }

    /**
     * {@inheritDoc}
     */
    @After
    @Override
    void tearDown() {
        delegate.tearDown()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    GradleRunner getGradleRunner() {
        delegate.getGradleRunner()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    BuildResult succeeds(List<String> arguments) {
        delegate.succeeds(arguments)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    BuildResult succeeds(String... arguments) {
        delegate.succeeds(arguments)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    BuildResult fails(List<String> arguments) {
        delegate.fails(arguments)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    BuildResult fails(String... arguments) {
        delegate.fails(arguments)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    TestFile getTestDirectory() {
        delegate.getTestDirectory()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    TestFile file(Object... path) {
        delegate.file(path)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    TestFile getBuildFile() {
        delegate.getBuildFile()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    TestFile getSettingsFile() {
        delegate.getSettingsFile()
    }
}
