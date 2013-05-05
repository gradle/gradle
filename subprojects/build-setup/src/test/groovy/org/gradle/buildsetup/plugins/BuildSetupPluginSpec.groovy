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

import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.util.HelperUtil
import org.gradle.util.Matchers
import spock.lang.Specification

class BuildSetupPluginSpec extends Specification {
    def project = HelperUtil.createRootProject()

    def "applies plugin"() {
        when:
        project.plugins.apply BuildSetupPlugin
        and:
        project.evaluate()
        then:
        project.tasks.wrapper instanceof Wrapper
        Matchers.dependsOn("wrapper").matches(project.tasks.setupBuild)
    }

    def "no wrapper task configured if build file already exists"() {
        setup:
        TemporaryFileProvider temporaryFileProvider = new TmpDirTemporaryFileProvider();
        File projectDir = temporaryFileProvider.createTemporaryDirectory("gradle", "projectDir");
        def buildFile = new File(projectDir, "build.gradle") << '// an empty build'
        buildFile << '// an empty build'
        project = HelperUtil.builder().withProjectDir(projectDir).build()
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
