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

import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.buildsetup.tasks.ConvertMaven2Gradle
import org.gradle.buildsetup.tasks.GenerateBuildFile
import org.gradle.util.HelperUtil
import org.gradle.util.Matchers
import spock.lang.Specification

class BuildSetupPluginSpec extends Specification {
    def project = HelperUtil.createRootProject()

    def "applies plugin"() {
        when:
        project.plugins.apply BuildSetupPlugin

        then:
        project.tasks.setupWrapper instanceof Wrapper
        Matchers.dependsOn("setupWrapper", "generateBuildFile", "generateSettingsFile").matches(project.tasks.setupBuild)
    }



    def "adds maven2Gradle task if pom exists"() {
        setup:
        project.file("pom.xml").createNewFile()

        when:
        project.plugins.apply BuildSetupPlugin

        then:
        project.tasks.maven2Gradle instanceof ConvertMaven2Gradle
        project.tasks.setupWrapper instanceof Wrapper
        Matchers.dependsOn("setupWrapper", "maven2Gradle").matches(project.tasks.setupBuild)
    }

    def "adds generateBuildFile task if no pom and no gradle build file exists"() {
        when:
        project.plugins.apply BuildSetupPlugin

        then:
        project.tasks.generateBuildFile instanceof GenerateBuildFile
        project.tasks.setupWrapper instanceof Wrapper
        Matchers.dependsOn("setupWrapper", "generateBuildFile", "generateSettingsFile").matches(project.tasks.setupBuild)
    }

    def "no build file generation if build file already exists"() {
        setup:
        project.file("build.gradle") << '// an empty build'

        when:
        project.plugins.apply BuildSetupPlugin

        then:
        project.setupBuild != null
        project.tasks.collect { it.name } == ["setupBuild"]
    }

    def "no build file generation if settings file already exists"() {
        setup:
        project.file("settings.gradle") << '// an empty file'

        when:
        project.plugins.apply BuildSetupPlugin

        then:
        project.setupBuild != null
        project.tasks.collect { it.name } == ["setupBuild"]
    }

    def "no build file generation when part of multi-project build"() {
        setup:
        HelperUtil.createChildProject(project, 'child')

        when:
        project.plugins.apply BuildSetupPlugin

        then:
        project.setupBuild != null
        project.tasks.collect { it.name } == ["setupBuild"]
    }
}
