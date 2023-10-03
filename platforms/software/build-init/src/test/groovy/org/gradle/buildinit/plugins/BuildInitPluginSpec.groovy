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

package org.gradle.buildinit.plugins


import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.initialization.SettingsState
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class BuildInitPluginSpec extends AbstractProjectBuilderSpec {
    def setup() {
        project.gradle.attachSettings(Stub(SettingsState))
    }

    def "applies plugin"() {
        when:
        project.pluginManager.apply BuildInitPlugin
        and:
        project.evaluate()
        then:
        project.tasks.wrapper instanceof Wrapper
        TaskDependencyMatchers.dependsOn("wrapper").matches(project.tasks.init)
    }

    def "no wrapper task configured if build file already exists"() {
        setup:
        project.file("build.gradle") << '// an empty file'

        when:
        project.pluginManager.apply(BuildInitPlugin)

        then:
        project.init != null
        project.tasks.collect { it.name } == ["init"]
    }

    def "no build file generation if settings file already exists"() {
        setup:
        project.file("settings.gradle") << '// an empty file'

        when:
        project.pluginManager.apply BuildInitPlugin

        then:
        project.init != null
        project.tasks.collect { it.name } == ["init"]
    }

    def "no build file generation when part of multi-project build"() {
        setup:
        TestUtil.createChildProject(project, 'child')

        when:
        project.pluginManager.apply BuildInitPlugin

        then:
        project.init != null
        project.tasks.collect { it.name } == ["init"]
    }
}
