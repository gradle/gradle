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

package org.gradle.ide.visualstudio.plugins

import org.gradle.ide.visualstudio.VisualStudioExtension
import org.gradle.ide.visualstudio.VisualStudioRootExtension
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class VisualStudioPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("root").build()

    def "adds extension to root project"() {
        when:
        project.pluginManager.apply(VisualStudioPlugin)

        then:
        project.visualStudio instanceof VisualStudioRootExtension
        project.visualStudio.solution.location.get().asFile == project.file("root.sln")
    }

    def "adds extension to child project"() {
        def child = ProjectBuilder.builder().withParent(project).withProjectDir(projectDir).withName("child").build()

        when:
        child.pluginManager.apply(VisualStudioPlugin)

        then:
        child.visualStudio instanceof VisualStudioExtension
    }

    def "adds 'openVisualStudio' task"() {
        when:
        project.pluginManager.apply(VisualStudioPlugin)

        then:
        project.tasks.openVisualStudio != null
    }

}
