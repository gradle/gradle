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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.Dependency
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class IdeaDependenciesProviderTest extends AbstractProjectBuilderSpec {
    private final project = TestUtil.createRootProject(temporaryFolder.testDirectory)
    private final childProject = TestUtil.createChildProject(project, "child", new File("."))
    private final artifactRegistry = Stub(IdeArtifactRegistry)
    private final dependenciesProvider = new IdeaDependenciesProvider(project, artifactRegistry, project.services.get(ProjectStateRegistry), { artifact -> null })

    def setup() {
        _ * artifactRegistry.getIdeProject(_, _) >> { Class c, def m ->
            return Stub(c)
        }
    }

    def "no dependencies test"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true

        when:
        def result = dependenciesProvider.provide(module)

        then:
        result.isEmpty()
    }

    def "dependencies are added to each required scope"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true

        when:
        project.dependencies.add('compile', project.layout.files('lib/guava.jar'))
        project.dependencies.add('testCompile', project.layout.files('lib/mockito.jar'))
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 2
        assertSingleLibrary(result, 'COMPILE', 'guava.jar')
        assertSingleLibrary(result, 'TEST', 'mockito.jar')
    }

    def "dependency is excluded if added to minus configuration"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        project.configurations.create('excluded')
        module.offline = true

        when:
        project.dependencies.add('testRuntime', project.layout.files('lib/guava.jar'))
        project.dependencies.add('excluded', project.layout.files('lib/guava.jar'))
        module.scopes.TEST.minus << project.configurations.getByName('excluded')
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 0
    }

    def "dependency is excluded if added to any minus configuration"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        project.configurations.create('excluded1')
        project.configurations.create('excluded2')
        module.offline = true

        when:
        project.dependencies.add('testRuntime', project.layout.files('lib/guava.jar'))
        project.dependencies.add('testRuntime', project.layout.files('lib/slf4j-api.jar'))
        project.dependencies.add('excluded1', project.layout.files('lib/guava.jar'))
        project.dependencies.add('excluded2', project.layout.files('lib/slf4j-api.jar'))
        module.scopes.TEST.minus << project.configurations.getByName('excluded1')
        module.scopes.TEST.minus << project.configurations.getByName('excluded2')
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 0
    }

    def "dependency is added from plus detached configuration"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module
        def extraDependency = project.dependencies.create(project.layout.files('lib/guava.jar'))
        def detachedCfg = project.configurations.detachedConfiguration(extraDependency)
        module.offline = true

        when:
        module.scopes.RUNTIME.plus << detachedCfg
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 1
        result.findAll { Dependency it -> it.scope == 'RUNTIME' }.size() == 1
    }

    def "compile dependency on child project"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')
        childProject.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true

        when:
        project.dependencies.add('compile', childProject)
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 1
        result.findAll { it.scope == 'COMPILE' }.size() == 1
    }

    def "testCompile dependency on current project (self-dependency)"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')
        childProject.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true

        when:
        project.dependencies.add('testCompile', project)
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 0
    }

    def "test and runtime scope for the same dependency"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true

        when:
        project.dependencies.add('testCompile', project.layout.files('lib/foo-impl.jar'))
        project.dependencies.add('runtime', project.layout.files('lib/foo-impl.jar'))
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 2
        assertSingleLibrary(result, 'TEST', 'foo-impl.jar')
        assertSingleLibrary(result, 'RUNTIME', 'foo-impl.jar')
    }

    def "compile only dependencies"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true

        when:
        project.dependencies.add('compileOnly', project.layout.files('lib/foo-api.jar'))
        project.dependencies.add('testRuntime', project.layout.files('lib/foo-impl.jar'))
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 2
        assertSingleLibrary(result, 'PROVIDED', 'foo-api.jar')
        assertSingleLibrary(result, 'TEST', 'foo-impl.jar')
    }

    def "compile only dependency conflicts with runtime dependencies"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true

        when:
        project.dependencies.add('compileOnly', project.layout.files('lib/foo-runtime.jar'))
        project.dependencies.add('compileOnly', project.layout.files('lib/foo-testRuntime.jar'))
        project.dependencies.add('runtime', project.layout.files('lib/foo-runtime.jar'))
        project.dependencies.add('testRuntime', project.layout.files('lib/foo-testRuntime.jar'))
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 2
        assertSingleLibrary(result, 'COMPILE', 'foo-runtime.jar')
        assertSingleLibrary(result, 'PROVIDED', 'foo-testRuntime.jar')
    }

    def "ignore unknown configurations"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true
        def extraConfiguration = project.configurations.create('extraConfiguration')

        when:
        project.dependencies.add('testCompile', project.layout.files('lib/mockito.jar'))
        def result = dependenciesProvider.provide(module)

        then:
        extraConfiguration.state == Configuration.State.UNRESOLVED
        result.size() == 1
        result.findAll { it.scope == 'TEST' }.size() == 1
    }

    private applyPluginToProjects() {
        project.apply plugin: IdeaPlugin
        childProject.apply plugin: IdeaPlugin
    }

    private void assertSingleLibrary(Set<Dependency> dependencies, String scope, String artifactName) {
        def size = dependencies.findAll { SingleEntryModuleLibrary module ->
            module.scope == scope && module.libraryFile.path.endsWith(artifactName)
        }.size()
        assert size == 1: "Expected single entry for artifact $artifactName in scope $scope but found $size"
    }
}
