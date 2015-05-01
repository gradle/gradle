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
import org.gradle.language.fixtures.TestJavaComponent
import org.junit.Rule

class JavaContinuousModeIntegrationTest extends AbstractIntegrationSpec {
    TestJavaComponent app = new TestJavaComponent()

    @Delegate @Rule ContinuousBuildTrigger buildTrigger = new ContinuousBuildTrigger(executer, this)

    GradleHandle getGradle() {
        buildTrigger.gradle
    }

    def "can build with continuous mode"() {
        given:
        def sourceFiles = app.writeSources(file("src/main"))
        app.writeResources(file("src/main/resources"))
        buildFile << """
        apply plugin: 'java'
"""
        when:
        startGradle("assemble")
        and:
        afterBuild {
            triggerNothing()
        }
        app.changeSources(sourceFiles)
        then:
        soFar {
            assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        }
        when:
        afterBuild {
            triggerRebuild()
        }
        then:
        soFar {
            assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
            assertTasksSkipped(":processResources")
            output.contains("REBUILD triggered due to file change")
        }
        when:
        afterBuild {
            triggerStop()
        }
        then:
        waitForStop()
        soFar {
            assertTasksSkipped(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        }
    }
}
