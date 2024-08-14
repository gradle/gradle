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

package org.gradle.buildinit.projectspecs.internal

import org.gradle.buildinit.plugins.AbstractInteractiveInitIntegrationSpec
import org.gradle.buildinit.plugins.TestsInitProjectSpecsViaPlugin
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.internal.TextUtil
import spock.lang.Ignore

@Ignore // TODO: Fails on CI where D-G included build is not available, remove when depending upon published version and not included build
class BuildInitPluginProjectSpecsInteractiveIntegrationTest extends AbstractInteractiveInitIntegrationSpec implements TestsInitProjectSpecsViaPlugin {
    @LeaksFileHandles
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

        // Select Custom Project Type
        ConcurrentTestUtil.poll(60) {
            assert handle.standardOutput.contains("1: Custom Project Type")
            assert handle.standardOutput.contains("2: Custom Project Type 2")
            assert !handle.standardOutput.contains("pom")
        }
        handle.stdinPipe.write(("1" + TextUtil.platformLineSeparator).bytes)

        closeInteractiveExecutor(handle)

        then:
        assertProjectFileGenerated("project.output", "MyGenerator created this Custom Project Type project.")
        assertWrapperGenerated()
    }

    private GradleHandle startInteractiveInit() {
        targetDir = file("new-project").with { createDir() }

        def args = ["init",
                    "-D${ AutoAppliedPluginHandler.INIT_PROJECT_SPEC_SUPPLIERS_PROP}=org.example.myplugin:1.0",
                    "--overwrite",
                    "--init-script", "../init.gradle"] as String[]

        println "Executing: '${args.join(" ")}')"
        println "Working Dir: '$targetDir'"

        executer.noDeprecationChecks() // TODO: We don't care about these here, they are from the declarative-prototype build, remove when depending upon published version and not included build
        return startInteractiveExecutorWithTasks(args)
    }
}
