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

import org.gradle.api.internal.artifacts.component.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.BuildIdentity
import org.gradle.initialization.DefaultBuildIdentity
import org.gradle.initialization.IncludedBuildExecuter
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.Dependency
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary
import org.gradle.plugins.ide.internal.IdeDependenciesExtractor
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

public class IdeaDependenciesProviderTest extends AbstractProjectBuilderSpec {
    private final ProjectInternal project = TestUtil.createRootProject(temporaryFolder.testDirectory)
    private final ProjectInternal childProject = TestUtil.createChildProject(project, "child", new File("."))
    def serviceRegistry = new DefaultServiceRegistry()
        .add(LocalComponentRegistry, Stub(LocalComponentRegistry))
        .add(IncludedBuildExecuter, Stub(IncludedBuildExecuter))
        .add(BuildIdentity, new DefaultBuildIdentity(new DefaultBuildIdentifier("foo")))
    private final dependenciesProvider = new IdeaDependenciesProvider(serviceRegistry)

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
        project.dependencies.add('compile', project.files('lib/guava.jar'))
        project.dependencies.add('testCompile', project.files('lib/mockito.jar'))
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 4
        assertSingleLibrary(result, 'PROVIDED', 'guava.jar')
        assertSingleLibrary(result, 'RUNTIME', 'guava.jar')
        assertSingleLibrary(result, 'TEST', 'guava.jar')
        assertSingleLibrary(result, 'TEST', 'mockito.jar')
    }

    def "dependency is excluded if added to minus configuration"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        project.configurations.create('excluded')
        module.offline = true

        when:
        project.dependencies.add('testRuntime', project.files('lib/guava.jar'))
        project.dependencies.add('excluded', project.files('lib/guava.jar'))
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
        project.dependencies.add('testRuntime', project.files('lib/guava.jar'))
        project.dependencies.add('testRuntime', project.files('lib/slf4j-api.jar'))
        project.dependencies.add('excluded1', project.files('lib/guava.jar'))
        project.dependencies.add('excluded2', project.files('lib/slf4j-api.jar'))
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
        def extraDependency = project.dependencies.create(project.files('lib/guava.jar'))
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
        result.size() == 3
        result.findAll { it.scope == 'PROVIDED' }.size() == 1
        result.findAll { it.scope == 'RUNTIME' }.size() == 1
        result.findAll { it.scope == 'TEST' }.size() == 1
    }

    def "test and runtime scope for the same dependency"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true

        when:
        project.dependencies.add('testCompile', project.files('lib/foo-impl.jar'))
        project.dependencies.add('runtime', project.files('lib/foo-impl.jar'))
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
        project.dependencies.add('compileOnly', project.files('lib/foo-api.jar'))
        project.dependencies.add('testRuntime', project.files('lib/foo-impl.jar'))
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
        project.dependencies.add('compileOnly', project.files('lib/foo-runtime.jar'))
        project.dependencies.add('compileOnly', project.files('lib/foo-testRuntime.jar'))
        project.dependencies.add('runtime', project.files('lib/foo-runtime.jar'))
        project.dependencies.add('testRuntime', project.files('lib/foo-testRuntime.jar'))
        def result = dependenciesProvider.provide(module)

        then:
        result.size() == 5
        assertSingleLibrary(result, 'PROVIDED', 'foo-runtime.jar')
        assertSingleLibrary(result, 'PROVIDED', 'foo-testRuntime.jar')
        assertSingleLibrary(result, 'RUNTIME', 'foo-runtime.jar')
        assertSingleLibrary(result, 'TEST', 'foo-runtime.jar')
        assertSingleLibrary(result, 'TEST', 'foo-testRuntime.jar')
    }

    def "ignore unknown configurations"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')

        def dependenciesExtractor = Spy(IdeDependenciesExtractor)
        def dependenciesProvider = new IdeaDependenciesProvider(dependenciesExtractor, serviceRegistry)
        def module = project.ideaModule.module // Mock(IdeaModule)
        module.offline = true
        def extraConfiguration = project.configurations.create('extraConfiguration')

        when:
        project.dependencies.add('testCompile', project.files('lib/mockito.jar'))
        def result = dependenciesProvider.provide(module)

        then:
        // only for compileClasspath, runtimeClasspath, testCompileClasspath, testRuntimeClasspath
        4 * dependenciesExtractor.extractProjectDependencies(_, { !it.contains(extraConfiguration) }, _)
        4 * dependenciesExtractor.extractLocalFileDependencies({ !it.contains(extraConfiguration) }, _)
        // offline: 4 * dependenciesExtractor.extractRepoFileDependencies(_, { !it.contains(extraConfiguration) }, _, _, _)
        0 * dependenciesExtractor._
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
        assert size == 1 : "Expected single entry for artifact $artifactName in scope $scope but found $size"
    }
}
