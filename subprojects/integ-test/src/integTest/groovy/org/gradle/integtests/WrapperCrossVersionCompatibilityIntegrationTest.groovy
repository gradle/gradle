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

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.integtests.fixtures.*

@RunWith(CrossVersionCompatibilityTestRunner)
class WrapperCrossVersionCompatibilityIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    BasicGradleDistribution previousVersion

    @Test
    public void canUseWrapperFromPreviousVersionToRunCurrentVersion() {
        checkWrapperWorksWith(previousVersion, dist)
    }

    @Test
    public void canUseWrapperFromCurrentVersionToRunPreviousVersion() {
        checkWrapperWorksWith(dist, previousVersion)
    }

    def checkWrapperWorksWith(BasicGradleDistribution wrapperGenVersion, BasicGradleDistribution executionVersion) {
        if (!wrapperGenVersion.wrapperCanExecute(executionVersion.version)) {
            println "skipping $wrapperGenVersion as its wrapper cannot execute version ${executionVersion.version}"
            return
        }

        println "use wrapper from $wrapperGenVersion to build using $executionVersion"

        dist.file('build.gradle') << """

task wrapper(type: Wrapper) {
    gradleVersion = '$executionVersion.version'
    urlRoot = '${executionVersion.binDistribution.parentFile.toURI()}'
}

task hello {
    doLast { println "hello from \$gradle.gradleVersion" }
}
"""

        executer(wrapperGenVersion).withTasks('wrapper').run()
        def result = executer(wrapperGenVersion).usingExecutable('gradlew').withTasks('hello').run()
        assert result.output.contains("hello from $executionVersion.version")
    }

    def executer(BasicGradleDistribution dist) {
        def executer = dist.executer();
        if (executer instanceof GradleDistributionExecuter) {
            executer.withDeprecationChecksDisabled()
        }
        executer.inDirectory(this.dist.testDir)
        return executer;
    }
}

