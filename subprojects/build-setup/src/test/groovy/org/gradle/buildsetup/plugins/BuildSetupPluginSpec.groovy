/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.buildsetup.plugins

import org.gradle.api.Project
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.buildsetup.tasks.ConvertMaven2Gradle
import org.gradle.buildsetup.tasks.GenerateBuildFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.Matchers
import org.junit.Rule
import spock.lang.Specification

class BuildSetupPluginSpec extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider();

    def "applies plugin"() {
        setup:
        def project = createProject()
        when:
        project.plugins.apply BuildSetupPlugin

        then:
        project.tasks.setupWrapper instanceof Wrapper
        Matchers.dependsOn("setupWrapper", "generateBuildFile", "generateSettingsFile").matches(project.tasks.setupBuild)
    }



    def "adds maven2Gradle task if pom exists"() {
        setup:
        def project = createProject()
        and:
        project.file("pom.xml").createNewFile()
        when:
        project.plugins.apply BuildSetupPlugin

        then:
        project.tasks.maven2Gradle instanceof ConvertMaven2Gradle
        project.tasks.setupWrapper instanceof Wrapper
        Matchers.dependsOn("setupWrapper", "maven2Gradle").matches(project.tasks.setupBuild)
    }

    def "adds generateBuildFile task if no pom and no gradle build file exists"() {
        setup:
        def project = createProject()
        when:
        project.plugins.apply BuildSetupPlugin
        then:
        project.tasks.generateBuildFile instanceof GenerateBuildFile
        project.tasks.setupWrapper instanceof Wrapper
        Matchers.dependsOn("setupWrapper", "generateBuildFile", "generateSettingsFile").matches(project.tasks.setupBuild)
    }

    def "no build file generation if files already exists"() {
        setup:
        def project = createProject(true)
        when:
        project.plugins.apply BuildSetupPlugin
        then:
        project.setupBuild != null
        project.tasks.collect { it.name } == ["setupBuild", "setupWrapper"]
    }

    private Project createProject(boolean withBuildScriptFile = false) {
        ProjectBuilder builder = ProjectBuilder.builder()
        if (withBuildScriptFile) {
            def projectDir = testDirectoryProvider.createDir("projectdir")
            projectDir.createFile("build.gradle")
            builder.withProjectDir(projectDir).build()
        } else {
            builder.build()
        }
    }
}
