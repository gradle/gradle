/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.resolver.NullGradleApiSourcesResolver
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class EclipseDependenciesCreatorTest extends AbstractProjectBuilderSpec {
    private ProjectInternal childProject
    private EclipseClasspath eclipseClasspath
    private EclipseDependenciesCreator dependenciesProvider

    def setup() {
        childProject = TestUtil.createChildProject(project, "child", temporaryFolder.testDirectory.file("child"))
        eclipseClasspath = TestUtil.newInstance(EclipseClasspath, project)
        dependenciesProvider = new EclipseDependenciesCreator(
            eclipseClasspath,
            project.services.get(IdeArtifactRegistry),
            NullGradleApiSourcesResolver.INSTANCE,
            false
        )
    }

    def "compile dependency on child project"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')
        childProject.apply(plugin: 'java')

        eclipseClasspath.setProjectDependenciesOnly(true)
        eclipseClasspath.plusConfigurations = [project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath]

        when:
        project.dependencies.add('implementation', childProject)
        def result = dependenciesProvider.createDependencyEntries()

        then:
        result.size() == 1
        result.findAll { it.kind == 'src' && it.path == '/child' }.size() == 1
    }

    def "testCompile dependency on current project (self-dependency)"() {
        applyPluginToProjects()
        project.apply(plugin: 'java')
        childProject.apply(plugin: 'java')

        eclipseClasspath.setProjectDependenciesOnly(true)
        eclipseClasspath.plusConfigurations = [project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath]

        when:
        project.dependencies.add('testImplementation', project)
        def result = dependenciesProvider.createDependencyEntries()

        then:
        result.size() == 0
    }

    private applyPluginToProjects() {
        project.apply plugin: EclipsePlugin
        childProject.apply plugin: EclipsePlugin
    }
}
