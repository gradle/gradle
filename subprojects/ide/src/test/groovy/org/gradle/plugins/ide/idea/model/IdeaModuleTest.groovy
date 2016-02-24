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

import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.util.TestUtil
import spock.lang.Specification

class IdeaModuleTest extends Specification {
    private final DefaultProject rootProject = TestUtil.createRootProject()
    private final DefaultProject moduleProject = TestUtil.createChildProject(rootProject, "child", new File("."))

    def "language level is null for non java projects"() {
        given:
        rootProject.getPlugins().apply(JavaPlugin)
        rootProject.getPlugins().apply(IdeaPlugin)
        def iml = Mock(IdeaModuleIml)
        def module = new IdeaModule(moduleProject, iml)
        expect:
        module.languageLevel == null
    }

    def "language level is null if idea project language level is explicitly set"() {
        given:
        rootProject.getPlugins().apply(IdeaPlugin)
        rootProject.getPlugins().apply(JavaPlugin)
        rootProject.idea.project.languageLevel = 1.6
        moduleProject.getPlugins().apply(JavaPlugin)
        moduleProject.sourceCompatibility = 1.7
        rootProject.sourceCompatibility = 1.8

        def iml = Mock(IdeaModuleIml)
        def module = new IdeaModule(moduleProject, iml)
        expect:
        module.languageLevel == null
    }

    def "language level is null if matching calculated idea project language level"() {
        given:
        rootProject.getPlugins().apply(IdeaPlugin)
        rootProject.getPlugins().apply(JavaPlugin)
        moduleProject.getPlugins().apply(JavaPlugin)
        moduleProject.sourceCompatibility = 1.5
        rootProject.sourceCompatibility = 1.5

        def iml = Mock(IdeaModuleIml)
        def module = new IdeaModule(moduleProject, iml)
        expect:
        module.languageLevel == null
    }

    def "target bytecode version is null for non java projects"() {
        given:
        rootProject.getPlugins().apply(JavaPlugin)
        rootProject.getPlugins().apply(IdeaPlugin)
        def iml = Mock(IdeaModuleIml)
        def module = new IdeaModule(moduleProject, iml)
        expect:
        module.targetBytecodeVersion == null
    }

   def "target bytecode version is null if matching calculated idea project bytecode version"() {
       given:
       rootProject.getPlugins().apply(IdeaPlugin)
       rootProject.getPlugins().apply(JavaPlugin)
       moduleProject.getPlugins().apply(JavaPlugin)
       moduleProject.targetCompatibility = 1.5
       rootProject.targetCompatibility = 1.5

       def iml = Mock(IdeaModuleIml)
       def module = new IdeaModule(moduleProject, iml)
       expect:
       module.targetBytecodeVersion == null
   }
}
