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

import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.GradleExecuter
import org.gradle.util.Jvm

@RunWith(DistributionIntegrationTestRunner.class)
class BackwardsCompatibilityIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources resources = new TestResources()
    private final GradleExecuter gradle08 = dist.previousVersion('0.8')
    private final GradleExecuter gradle09rc1 = dist.previousVersion('0.9-rc-1')
    private final GradleExecuter gradle09rc2 = dist.previousVersion('0.9-rc-2')

    @Test
    public void canBuildJavaProject() {
        dist.testFile('buildSrc/src/main/groovy').assertIsDir()

        // Upgrade and downgrade
        eachVersion([gradle08, gradle09rc1, gradle09rc2, executer, gradle09rc2, gradle09rc1, gradle08]) { version ->
            version.inDirectory(dist.testDir).withTasks('build').run()
        }
    }

    def eachVersion(Iterable<GradleExecuter> versions, Closure cl) {
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

