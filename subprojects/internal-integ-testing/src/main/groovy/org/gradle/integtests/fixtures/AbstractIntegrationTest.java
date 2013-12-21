/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.collect.Sets;
import org.gradle.integtests.fixtures.executer.*;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.test.fixtures.ivy.IvyFileRepository;
import org.gradle.test.fixtures.maven.MavenFileRepository;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

import java.io.File;
import java.util.Set;

public abstract class AbstractIntegrationTest implements TestDirectoryProvider {
    @Rule public final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider();
    public final GradleDistribution distribution = new UnderDevelopmentGradleDistribution();
    public final GradleExecuter executer = new GradleContextualExecuter(distribution, this);

    @ClassRule public static final TestNameTestDirectoryProvider SHARED_TEST_DIRECTORY_PROVIDER = new TestNameTestDirectoryProvider();
    public static final GradleDistribution SHARED_DISTRIBUTION = new UnderDevelopmentGradleDistribution();
    private static final GradleExecuter SHARED_EXECUTER = new GradleContextualExecuter(SHARED_DISTRIBUTION, SHARED_TEST_DIRECTORY_PROVIDER);

    private static final Set<Class<?>> SHARED_BUILD_RUN_CLASSES = Sets.newHashSet();

    protected boolean useSharedBuild;

    private MavenFileRepository mavenRepo;
    private IvyFileRepository ivyRepo;

    protected GradleDistribution getDistribution() {
        return useSharedBuild ? SHARED_DISTRIBUTION : distribution;
    }

    protected GradleExecuter getExecuter() {
        return useSharedBuild ? SHARED_EXECUTER : executer;
    }

    protected TestNameTestDirectoryProvider getTestDirectoryProvider() {
        return useSharedBuild ? SHARED_TEST_DIRECTORY_PROVIDER : testDirectoryProvider;
    }

    protected void runSharedBuild() {}

    @Before
    public void doRunSharedBuild() {
        if (SHARED_BUILD_RUN_CLASSES.add(getClass())) {
            useSharedBuild = true;
            runSharedBuild();
            useSharedBuild = false;
        }
    }

    public TestFile getTestDirectory() {
        return getTestDirectoryProvider().getTestDirectory();
    }

    public TestFile file(Object... path) {
        return getTestDirectory().file(path);
    }

    public TestFile testFile(String name) {
        return file(name);
    }

    protected GradleExecuter inTestDirectory() {
        return inDirectory(getTestDirectory());
    }

    protected GradleExecuter inDirectory(File directory) {
        return getExecuter().inDirectory(directory);
    }

    protected GradleExecuter usingBuildFile(File file) {
        return getExecuter().usingBuildScript(file);
    }

    protected GradleExecuter usingProjectDir(File projectDir) {
        return getExecuter().usingProjectDirectory(projectDir);
    }

    protected ArtifactBuilder artifactBuilder() {
        GradleExecuter gradleExecuter = getDistribution().executer(testDirectoryProvider);
        gradleExecuter.withGradleUserHomeDir(getExecuter().getGradleUserHomeDir());
        return new GradleBackedArtifactBuilder(gradleExecuter, getTestDirectory().file("artifacts"));
    }

    public MavenFileRepository maven(TestFile repo) {
        return new MavenFileRepository(repo);
    }

    public MavenFileRepository maven(Object repo) {
        return new MavenFileRepository(file(repo));
    }

    public MavenFileRepository getMavenRepo() {
        if (mavenRepo == null) {
            mavenRepo = new MavenFileRepository(file("maven-repo"));
        }
        return mavenRepo;
    }

    public IvyFileRepository ivy(TestFile repo) {
        return new IvyFileRepository(repo);
    }

    public IvyFileRepository ivy(Object repo) {
        return new IvyFileRepository(file(repo));
    }

    public IvyFileRepository getIvyRepo() {
        if (ivyRepo == null) {
            ivyRepo = new IvyFileRepository(file("ivy-repo"));
        }
        return ivyRepo;
    }
}
