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

package org.gradle.ide.xcode.plugins

import org.gradle.ide.xcode.XcodeExtension
import org.gradle.ide.xcode.XcodeRootExtension
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification


class XcodePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("root").build()

    def "adds extension to root project"() {
        when:
        project.pluginManager.apply(XcodePlugin)

        then:
        project.xcode instanceof XcodeRootExtension
        project.xcode.workspace.location.get().asFile == project.file("root.xcworkspace")
    }

    def "adds extension to child project"() {
        def child = ProjectBuilder.builder().withParent(project).withProjectDir(projectDir).withName("child").build()

        when:
        child.pluginManager.apply(XcodePlugin)

        then:
        child.xcode instanceof XcodeExtension
    }

    def "adds 'openXcode' task"() {
        when:
        project.pluginManager.apply(XcodePlugin)

        then:
        project.tasks.openXcode != null
    }
}
