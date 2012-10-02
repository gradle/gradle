/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures;

import org.gradle.util.TestFile;
import org.gradle.util.TestFileContext;
import org.junit.Rule;

import java.io.File;

public abstract class AbstractIntegrationTest implements TestFileContext {
    @Rule public final GradleDistribution distribution = new GradleDistribution();
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter();

    public TestFile getTestDir() {
        return distribution.getTestDir();
    }

    public TestFile file(Object... path) {
        return getTestDir().file(path);
    }

    public TestFile testFile(String name) {
        return file(name);
    }

    protected GradleExecuter inTestDirectory() {
        return inDirectory(getTestDir());
    }

    protected GradleExecuter inDirectory(File directory) {
        return executer.inDirectory(directory);
    }

    protected GradleExecuter usingBuildFile(File file) {
        return executer.usingBuildScript(file);
    }

    protected GradleExecuter usingProjectDir(File projectDir) {
        return executer.usingProjectDirectory(projectDir);
    }

    protected ArtifactBuilder artifactBuilder() {
        GradleDistributionExecuter gradleExecuter = distribution.executer();
        gradleExecuter.withUserHomeDir(distribution.getUserHomeDir());
        return new GradleBackedArtifactBuilder(gradleExecuter, getTestDir().file("artifacts"));
    }

    public MavenRepository maven(TestFile repo) {
        return new MavenRepository(repo);
    }
}
