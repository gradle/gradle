/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests

import org.junit.runner.RunWith
import org.junit.Test
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.junit.Rule
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.AbstractGradleExecuter
import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.ExecutionFailure
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.GradleExecuter
import org.gradle.util.TestFile
import org.gradle.integtests.fixtures.ForkingGradleExecuter

@RunWith(DistributionIntegrationTestRunner.class)
class BackwardsCompatibilityIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources resources = new TestResources()
    private final GradleExecuter gradle08 = new PreviousGradleVersionExecuter(version: '0.8', dist: dist)
    private final GradleExecuter gradle09preview3 = new PreviousGradleVersionExecuter(version: '0.9-preview-3', dist: dist)

    @Test
    public void canBuildJavaProject() {
        dist.testFile('buildSrc/src/main/groovy').assertIsDir()

        // Upgrade and downgrade
        eachVersion([gradle08, gradle09preview3, executer, gradle09preview3, gradle08]) { version ->
            version.inDirectory(dist.testDir).withTasks('build').run()
        }
    }

    def eachVersion(Iterable<GradleExecuter> versions, Closure cl) {
        versions.each { version ->
            try {
                System.out.println("building using $version");
                cl.call(version)
            } catch (Throwable t) {
                throw new RuntimeException("Could not build test project using $version.", t)
            }
        }
    }
}

class PreviousGradleVersionExecuter extends AbstractGradleExecuter {
    def GradleDistribution dist
    def String version

    def String toString() {
        "Gradle $version"
    }

    protected ExecutionResult doRun() {
        TestFile gradleHome = findGradleHome()

        ForkingGradleExecuter executer = new ForkingGradleExecuter(gradleHome)
        copyTo(executer)
        return executer.run()
    }

    private TestFile findGradleHome() {
        // maybe download and unzip distribution
        TestFile versionsDir = dist.distributionsDir.parentFile.file('previousVersions')
        TestFile gradleHome = versionsDir.file("gradle-$version")
        TestFile markerFile = gradleHome.file('ok.txt')
        if (!markerFile.isFile()) {
            TestFile zipFile = dist.userHomeDir.parentFile.file("gradle-$version-bin.zip")
            if (!zipFile.isFile()) {
                try {
                    URL url = new URL("http://dist.codehaus.org/gradle/${zipFile.name}")
                    System.out.println("downloading $url");
                    zipFile.copyFrom(url)
                } catch (Throwable t) {
                    zipFile.delete()
                    throw t
                }
            }
            zipFile.usingNativeTools().unzipTo(versionsDir)
            markerFile.touch()
        }
        return gradleHome
    }

    protected ExecutionFailure doRunWithFailure() {
        throw new UnsupportedOperationException();
    }
}