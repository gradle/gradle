/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.buildinit.specs.internal

import org.gradle.buildinit.plugins.AbstractInteractiveInitIntegrationSpec
import org.gradle.buildinit.plugins.TestsBuildInitSpecsViaPlugin
import org.gradle.buildinit.plugins.fixtures.WrapperTestFixture
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.internal.TextUtil

@LeaksFileHandles
class BuildInitSpecsInteractiveIntegrationTest extends AbstractInteractiveInitIntegrationSpec implements TestsBuildInitSpecsViaPlugin {
    def "prompts to choose dynamically loaded project type properly and generates project when selected"() {
        given:
        publishTestPlugin()

        when:
        def handle = startInteractiveInit()

        // Select 'yes'
        ConcurrentTestUtil.poll(120) {
            assert handle.standardOutput.contains("Additional project types were loaded.  Do you want to generate a project using a contributed project specification? (default: yes) [yes, no]")
        }
        handle.stdinPipe.write(("yes" + TextUtil.platformLineSeparator).bytes)

        // Select First Project Type
        ConcurrentTestUtil.poll(60) {
            assert handle.standardOutput.contains("1: First Project Type")
            assert handle.standardOutput.contains("2: Second Project Type")
            assert !handle.standardOutput.contains("pom")
        }
        handle.stdinPipe.write(("1" + TextUtil.platformLineSeparator).bytes)

        ConcurrentTestUtil.poll(60) {
            assert handle.standardOutput.contains("Generate 'First Project Type'")
        }
        closeInteractiveExecutor(handle)

        then:
        assertProjectFileGenerated("project.output", "MyGenerator created this First Project Type project.")
        assertWrapperGenerated()
    }

    def "can cancel generation"() {
        given:
        publishTestPlugin()

        when:
        def handle = startInteractiveInit()

        // Select 'yes'
        ConcurrentTestUtil.poll(120) {
            assert handle.standardOutput.contains("Additional project types were loaded.  Do you want to generate a project using a contributed project specification? (default: yes) [yes, no]")
        }
        handle.stdinPipe.write(("yes" + TextUtil.platformLineSeparator).bytes)

        // Select First Project Type
        ConcurrentTestUtil.poll(60) {
            assert handle.standardOutput.contains("1: First Project Type")
            assert handle.standardOutput.contains("2: Second Project Type")
            assert !handle.standardOutput.contains("pom")
        }
        // Interrupt input
        handle.stdinPipe.close()
        def result = handle.waitForFailure()

        then:
        result.assertHasDescription("Execution failed for task ':init'.")
        result.assertHasCause("Build cancelled.")
        and:
        file("new-project/project.output").assertDoesNotExist()
        new WrapperTestFixture(targetDir).notGenerated()
    }


    private GradleHandle startInteractiveInit() {
        targetDir = file("new-project").createDir()

        def args = ["init",
                    "-D${ BuildInitSpecRegistry.BUILD_INIT_SPECS_PLUGIN_SUPPLIER}=org.example.myplugin:1.0",
                    "--overwrite",
                    "--init-script", "../init.gradle"] as String[]

        println "Executing: '${args.join(" ")}')"
        println "Working Dir: '$targetDir'"

        return startInteractiveExecutorWithTasks(args)
    }
}
