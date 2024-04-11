/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.executer


import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

/**
 * Commit distribution is a distribution built from a commit.
 * Its version looks like "7.5-commit-1a2b3c".
 *
 * The commit distributions are generated at the following location:
 *
 * +-- build
 *    +-- commit-distributions
 *        +-- gradle-7.5-commit-1a2b3c4.zip
 *        +-- gradle-7.5-commit-1a2b3c4-tooling-api.jar
 *        \-- gradle-7.5-commit-1a2b3c4
 *            \-- gradle-7.5-20220618071843+0000
 *                +-- bin
 *                +-- lib
 *                \-- ..
 */
class CommitDistribution extends DefaultGradleDistribution {
    private final TestFile commitDistributionsDir;

    CommitDistribution(String version, TestFile commitDistributionsDir) {
        super(GradleVersion.version(version), commitDistributionsDir.file("gradle-$version"), commitDistributionsDir.file("gradle-${version}.zip"))
        this.commitDistributionsDir = commitDistributionsDir;
    }

    /**
     * `super.gradleHome` is not the real Gradle home but the directory which the commit distribution is unzipped into.
     * The real Gradle home is `gradle-7.5-commit-1a2b3c4/gradle-7.5-20220618071843+0000`.
     * @return
     */
    @Override
    TestFile getGradleHomeDir() {
        TestFile superGradleHome = super.getGradleHomeDir()

        if (!superGradleHome.isDirectory() || superGradleHome.listFiles({ it.isDirectory() } as FileFilter).size() == 0) {
            super.binDistribution.usingNativeTools().unzipTo(superGradleHome)
        }
        return new TestFile(superGradleHome.listFiles({ it.isDirectory() } as FileFilter).first())
    }

    static boolean isCommitDistribution(String version) {
        return version.contains("-commit-")
    }

    static File getToolingApiJar(String version) {
        return IntegrationTestBuildContext.INSTANCE.getGradleUserHomeDir().parentFile.file("commit-distributions/gradle-${version}-tooling-api.jar")
    }
}
