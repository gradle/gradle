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
import org.gradle.testkit.runner.internal.fixtures.DefaultFunctionalTestSupport;
import org.gradle.testkit.runner.internal.file.JUnitTemporaryDirectoryProvider;
import org.junit.After;
import org.junit.Before;

import java.util.List;

/**
 * Test fixture for functional testing with TestKit compatible with JUnit.
 *
 * @since 4.2
 */
@Incubating
public abstract class JUnitFunctionalTest implements FunctionalTestSupport {

    private final FunctionalTestSupport delegate = new DefaultFunctionalTestSupport(new JUnitTemporaryDirectoryProvider());

    /**
     * {@inheritDoc}
     */
    @Before
    @Override
    public void initialize() {
        delegate.initialize();
    }

    /**
     * {@inheritDoc}
     */
    @After
    @Override
    public void tearDown() {
        delegate.tearDown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GradleRunner getGradleRunner() {
        return delegate.getGradleRunner();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildResult succeeds(List<String> arguments) {
        return delegate.succeeds(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildResult succeeds(String... arguments) {
        return delegate.succeeds(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildResult fails(List<String> arguments) {
        return delegate.fails(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildResult fails(String... arguments) {
        return delegate.fails(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestFile getTestDirectory() {
        return delegate.getTestDirectory();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestFile file(Object... path) {
        return delegate.file(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestFile getBuildFile() {
        return delegate.getBuildFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestFile getSettingsFile() {
        return delegate.getSettingsFile();
    }
}
