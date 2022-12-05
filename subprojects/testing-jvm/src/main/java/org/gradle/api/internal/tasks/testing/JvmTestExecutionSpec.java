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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.Path;

import java.io.File;
import java.util.Set;

@UsedByScanPlugin("test-distribution, test-retry")
public class JvmTestExecutionSpec implements TestExecutionSpec {
    private final TestFramework testFramework;
    private final Iterable<? extends File> classpath;
    private final Iterable<? extends File> modulePath;
    private final FileTree candidateClassFiles;
    private final boolean scanForTestClasses;
    private final FileCollection testClassesDirs;
    private final String path;
    private final Path identityPath;
    private final long forkEvery;
    private final JavaForkOptions javaForkOptions;
    private final int maxParallelForks;
    private final Set<String> previousFailedTestClasses;
    private final boolean testIsModule;

    public JvmTestExecutionSpec(TestFramework testFramework, Iterable<? extends File> classpath, Iterable<? extends File>  modulePath, FileTree candidateClassFiles, boolean scanForTestClasses, FileCollection testClassesDirs, String path, Path identityPath, long forkEvery, JavaForkOptions javaForkOptions, int maxParallelForks, Set<String> previousFailedTestClasses, boolean testIsModule) {
        this.testFramework = testFramework;
        this.classpath = classpath;
        this.modulePath = modulePath;
        this.candidateClassFiles = candidateClassFiles;
        this.scanForTestClasses = scanForTestClasses;
        this.testClassesDirs = testClassesDirs;
        this.path = path;
        this.identityPath = identityPath;
        this.forkEvery = forkEvery;
        this.javaForkOptions = javaForkOptions;
        this.maxParallelForks = maxParallelForks;
        this.previousFailedTestClasses = previousFailedTestClasses;
        this.testIsModule = testIsModule;
    }

    @SuppressWarnings("unused")
    @UsedByScanPlugin("test-retry")
    public JvmTestExecutionSpec copyWithTestFramework(TestFramework testFramework) {
        return new JvmTestExecutionSpec(testFramework, this.classpath, this.modulePath, this.candidateClassFiles,
            this.scanForTestClasses, this.testClassesDirs, this.path, this.identityPath, this.forkEvery,
            this.javaForkOptions, this.maxParallelForks, this.previousFailedTestClasses, this.testIsModule
        );
    }

    public TestFramework getTestFramework() {
        return testFramework;
    }

    public Iterable<? extends File> getClasspath() {
        return classpath;
    }

    public Iterable<? extends File> getModulePath() {
        return modulePath;
    }

    public FileTree getCandidateClassFiles() {
        return candidateClassFiles;
    }

    public boolean isScanForTestClasses() {
        return scanForTestClasses;
    }

    @UsedByScanPlugin("test-retry")
    public FileCollection getTestClassesDirs() {
        return testClassesDirs;
    }

    public String getPath() {
        return path;
    }

    public Path getIdentityPath() {
        return identityPath;
    }

    public long getForkEvery() {
        return forkEvery;
    }

    @UsedByScanPlugin("test-distribution")
    public JavaForkOptions getJavaForkOptions() {
        return javaForkOptions;
    }

    public int getMaxParallelForks() {
        return maxParallelForks;
    }

    public Set<String> getPreviousFailedTestClasses() {
        return previousFailedTestClasses;
    }

    public boolean getTestIsModule() {
        return testIsModule;
    }
}
