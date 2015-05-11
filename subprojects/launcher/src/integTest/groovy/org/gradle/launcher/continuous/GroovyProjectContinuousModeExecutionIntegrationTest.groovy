/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ContinuousBuildTrigger
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.JDK7_OR_LATER)
class GroovyProjectContinuousModeExecutionIntegrationTest extends AbstractIntegrationSpec {
    @Delegate @Rule ContinuousBuildTrigger buildTrigger = new ContinuousBuildTrigger(executer, this)

    GradleHandle getGradle() {
        return buildTrigger.gradle
    }

    def "can enable continuous mode in empty groovy project"() {
        when:
        buildFile << '''
apply plugin:'groovy'
'''
        testDirectory.createDir('src/main/groovy')
        startGradle('classes')
        and:
        afterBuild {
            triggerStop()
        }
        then:
        waitForStop()
        !gradle.standardOutput.contains("FAILURE")
        gradle.standardOutput.contains("BUILD SUCCESSFUL")
    }
}
