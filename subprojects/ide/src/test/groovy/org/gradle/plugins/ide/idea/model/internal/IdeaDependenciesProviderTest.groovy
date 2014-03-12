/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.idea.model.internal

import org.gradle.api.internal.project.DefaultProject
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.util.TestUtil
import spock.lang.Specification

public class IdeaDependenciesProviderTest extends Specification {
    private final DefaultProject project = TestUtil.createRootProject()
    // private final Project childProject = TestUtil.createChildProject(project, "child", new File("."))

    def "no dependencies test"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def dependenciesProvider = new IdeaDependenciesProvider()
        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true

        when:
        def result = dependenciesProvider.provide(module)

        then:
        result.isEmpty()
    }

    def "common dependencies"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def dependenciesProvider = new IdeaDependenciesProvider()
        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true

        when:
        project.dependencies.add('compile', project.files('lib/guava.jar'))
        project.dependencies.add('testCompile', project.files('lib/mockito.jar'))
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 2
        result.findAll { it.scope == 'COMPILE' }.size() == 1
        result.findAll { it.scope == 'TEST' }.size() == 1
    }

    private applyPluginToProjects() {
        project.apply plugin: IdeaPlugin
        // childProject.apply plugin: IdeaPlugin
    }
}
