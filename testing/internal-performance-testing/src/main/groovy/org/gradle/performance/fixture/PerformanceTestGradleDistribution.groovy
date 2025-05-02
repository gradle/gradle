/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.NoDaemonGradleExecuter
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.GFileUtils

/**
 * Gradle's performance slightly depends on the length of the Gradle home path. This
 * class ensures fairness between the version under development and the baseline versions,
 * which live at different depth inside the Gradle repository.
 */
@CompileStatic
class PerformanceTestGradleDistribution implements GradleDistribution {
    @Delegate
    final GradleDistribution delegate
    final File testDir

    private TestFile gradleHome

    PerformanceTestGradleDistribution(GradleDistribution delegate, File testDir) {
        this.delegate = delegate
        this.testDir = testDir
    }

    TestFile getGradleHomeDir() {
        if (!gradleHome) {
            gradleHome = new TestFile(testDir.parentFile, testDir.name + "-gradle-home")
            GFileUtils.copyDirectory(delegate.gradleHomeDir, gradleHome)
            gradleHome.file("bin/gradle").setExecutable(true, true)
        }
        gradleHome
    }

    GradleExecuter executer(TestDirectoryProvider testDirectoryProvider, IntegrationTestBuildContext buildContext) {
        return new NoDaemonGradleExecuter(this, testDirectoryProvider, version, buildContext)
    }

}
