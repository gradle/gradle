/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.idea.model

import org.gradle.api.JavaVersion
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class IdeaProjectTest extends AbstractProjectBuilderSpec {
    private ProjectInternal childProject
    private ProjectInternal anotherChildProject

    def setup() {
        childProject = TestUtil.createChildProject(project, "child", new File("."))
        anotherChildProject = TestUtil.createChildProject(project, "child2", new File("."))
    }

    def "location tracks change to outputFile property"() {
        when:
        project.pluginManager.apply(IdeaPlugin)
        def location = project.idea.project.location

        then:
        project.idea.project.outputFile == project.file("test-project.ipr")
        location.get().asFile == project.idea.project.outputFile

        when:
        project.idea.project.outputFile = project.file("other.ipr")

        then:
        location.get().asFile == project.idea.project.outputFile
    }

    def "project bytecode version set to highest module targetCompatibility"() {
        when:
        project.apply plugin: IdeaPlugin
        childProject.apply plugin: IdeaPlugin
        anotherChildProject.apply plugin: IdeaPlugin
        project.apply(plugin: JavaPlugin)
        childProject.apply(plugin: JavaPlugin)
        anotherChildProject.apply(plugin: JavaPlugin)

        and:
        project.targetCompatibility = JavaVersion.VERSION_1_5
        childProject.targetCompatibility = JavaVersion.VERSION_1_6
        anotherChildProject.targetCompatibility = JavaVersion.VERSION_1_7

        then:
        project.idea.project.targetBytecodeVersion == JavaVersion.VERSION_1_7
    }

    /**
     * to be in sync with current language level settings were we also use 1.6 as default.
     * */
    def "project bytecode version set to 1.6 for if no java modules involved"() {
        when:
        project.apply plugin: IdeaPlugin
        childProject.apply plugin: IdeaPlugin
        anotherChildProject.apply plugin: IdeaPlugin

        then:
        project.idea.project.targetBytecodeVersion == JavaVersion.VERSION_1_6
    }
}
