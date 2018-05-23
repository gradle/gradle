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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class IdeaModuleTest extends AbstractProjectBuilderSpec {
    private final ProjectInternal rootProject = TestUtil.createRootProject(temporaryFolder.testDirectory)
    private final ProjectInternal moduleProject = TestUtil.createChildProject(rootProject, "child", new File("."))

    def "language level is null for non java projects"() {
        given:
        rootProject.getPlugins().apply(JavaPlugin)
        rootProject.getPlugins().apply(IdeaPlugin)
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
