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

package org.gradle.buildinit.templates.internal

import org.gradle.buildinit.plugins.AbstractInteractiveInitIntegrationSpec
import org.gradle.buildinit.plugins.TestsInitTemplatePlugin
import org.gradle.plugin.management.internal.template.TemplatePluginHandler
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.internal.TextUtil

class BuildInitPluginTemplatesInteractiveIntegrationTest extends AbstractInteractiveInitIntegrationSpec implements TestsInitTemplatePlugin {
    @LeaksFileHandles
    def "prompts to choose template properly"() {
        given:
        publishTestPlugin()

        when:
        targetDir = file("new-project").with { createDir() }

        def handle = startInteractiveExecutorWithTasks(
            "init",
            "-D${ TemplatePluginHandler.TEMPLATE_PLUGINS_PROP}=org.example.myplugin:1.0",
            "--overwrite",
            "--init-script", "../init.gradle"
        )

        // Select 'yes'
        ConcurrentTestUtil.poll(60) {
            assert handle.standardOutput.contains("Templates found.  Do you want to generate a project using a template? (default: yes) [yes, no]")
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
}
