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

import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.TestResources
import org.gradle.util.Jvm
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DistributionIntegrationTestRunner.class)
class CrossVersionCompatibilityIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final TestResources resources = new TestResources()
    private final BasicGradleDistribution gradle08 = dist.previousVersion('0.8')
    private final BasicGradleDistribution gradle09rc1 = dist.previousVersion('0.9-rc-1')
    private final BasicGradleDistribution gradle09rc2 = dist.previousVersion('0.9-rc-2')

    @Test
    public void canBuildJavaProject() {
        dist.testFile('buildSrc/src/main/groovy').assertIsDir()

        // Upgrade and downgrade
        eachVersion([gradle08, gradle09rc1, gradle09rc2, dist, gradle09rc2, gradle09rc1, gradle08]) { version ->
            version.executer().inDirectory(dist.testDir).withTasks('build').run()
        }
    }

    @Test
    public void canUseWrapperFromPreviousVersionToRunCurrentVersion() {
        eachVersion([gradle09rc1, gradle09rc2]) { version ->
            checkWrapperWorksWith(version, dist)
        }
    }

    @Test
    public void canUseWrapperFromCurrentVersionToRunPreviousVersion() {
        eachVersion([gradle09rc1, gradle09rc2]) { version ->
            checkWrapperWorksWith(dist, version)
        }
    }

    def checkWrapperWorksWith(BasicGradleDistribution wrapperGenVersion, BasicGradleDistribution executionVersion) {
        wrapperGenVersion.executer().withTasks('wrapper').withArguments("-PdistZip=$executionVersion.binDistribution.absolutePath", "-PdistVersion=$executionVersion.version").run()
        def result = wrapperGenVersion.executer().usingExecutable('gradlew').withTasks('hello').run()
        assert result.output.contains("hello from $executionVersion.version")
    }

    def eachVersion(Iterable<BasicGradleDistribution> versions, Closure cl) {
        versions.each { version ->
            if (!version.worksWith(Jvm.current())) {
                System.out.println("skipping $version as it does not work with ${Jvm.current()}.")
                return
            }
            try {
                System.out.println("building using $version");
                cl.call(version)
            } catch (Throwable t) {
                throw new RuntimeException("Could not build test project using $version.", t)
            }
        }
    }
}

