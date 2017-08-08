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

package org.gradle.testkit.runner.internal.fixtures;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.fixtures.FunctionalTestSupport;
import org.gradle.testkit.runner.fixtures.file.TestFile;
import org.gradle.testkit.runner.internal.file.TemporaryDirectoryProvider;

import java.util.Arrays;
import java.util.List;

public class DefaultFunctionalTestSupport implements FunctionalTestSupport {

    private final TemporaryDirectoryProvider temporaryDirectoryProvider;
    private GradleRunner gradleRunner;
    private TestFile testDirectory;
    private TestFile buildFile;
    private TestFile settingsFile;

    public DefaultFunctionalTestSupport(TemporaryDirectoryProvider temporaryDirectoryProvider) {
        this.temporaryDirectoryProvider = temporaryDirectoryProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        temporaryDirectoryProvider.create();
        testDirectory = new TestFile(temporaryDirectoryProvider.getDirectory());
        buildFile = testDirectory.file("build.gradle");
        settingsFile = testDirectory.file("settings.gradle");
        gradleRunner = GradleRunner.create().withProjectDir(temporaryDirectoryProvider.getDirectory());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown() {
        temporaryDirectoryProvider.destroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GradleRunner getGradleRunner() {
        return gradleRunner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildResult succeeds(List<String> arguments) {
        return withArguments(arguments).build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildResult succeeds(String... arguments) {
        return succeeds(Arrays.asList(arguments));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildResult fails(List<String> arguments) {
        return withArguments(arguments).buildAndFail();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildResult fails(String... arguments) {
        return fails(Arrays.asList(arguments));
    }

    private GradleRunner withArguments(List<String> arguments) {
        gradleRunner.withArguments(arguments);
        return gradleRunner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestFile getTestDirectory() {
        return testDirectory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestFile file(Object... path) {
        return testDirectory.file(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestFile getBuildFile() {
        return buildFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestFile getSettingsFile() {
        return settingsFile;
    }
}
