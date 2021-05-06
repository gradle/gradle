/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.IProjectFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class InstantiatingBuildLoaderTest extends Specification {

    InstantiatingBuildLoader buildLoader
    IProjectFactory projectFactory
    File testDir
    File rootProjectDir
    File childProjectDir
    ProjectDescriptorRegistry projectDescriptorRegistry = new DefaultProjectDescriptorRegistry()
    def projectRegistry = Mock(ProjectRegistry)
    StartParameter startParameter = new StartParameter()
    ProjectDescriptor rootDescriptor
    ProjectInternal rootProject
    ProjectDescriptor childDescriptor
    ProjectInternal childProject
    GradleInternal gradle
    SettingsInternal settingsInternal
    BuildState buildState = Mock(BuildState)
    ProjectStateRegistry projectStateRegistry = Mock(ProjectStateRegistry)
    BuildIdentifier buildId = new DefaultBuildIdentifier("test")
    ProjectState rootProjectState = Mock(ProjectState)

    def rootProjectClassLoaderScope = Mock(ClassLoaderScope)
    def baseProjectClassLoaderScope = Mock(ClassLoaderScope) {
        1 * createChild("root-project[:]") >> rootProjectClassLoaderScope
    }

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def setup() {
        projectFactory = Mock(IProjectFactory)
        buildLoader = new InstantiatingBuildLoader()
        testDir = tmpDir.testDirectory
        (rootProjectDir = new File(testDir, 'root')).mkdirs()
        (childProjectDir = new File(rootProjectDir, 'child')).mkdirs()
        startParameter.currentDir = rootProjectDir
        rootDescriptor = descriptor('root', null, rootProjectDir)
        rootProject = project(rootDescriptor, null)
        childDescriptor = descriptor('child', rootDescriptor, childProjectDir)
        childProject = project(childDescriptor, rootProject)
        def services = new DefaultServiceRegistry()
        services.add(projectStateRegistry)
        gradle = Mock(GradleInternal) {
            getStartParameter() >> startParameter
            getRootProject() >> rootProject
            baseProjectClassLoaderScope() >> baseProjectClassLoaderScope
            getProjectRegistry() >> projectRegistry
            getIdentityPath() >> Path.ROOT
            getServices() >> services
            getOwner() >> buildState
        }
        settingsInternal = Mock(SettingsInternal) {
            getRootProject() >> rootDescriptor
        }
        buildState.buildIdentifier >> buildId
        projectStateRegistry.stateFor(buildId, Path.ROOT) >> rootProjectState
    }

    def createsBuildWithRootProjectAsTheDefaultOne() {
        given:
        settingsInternal.defaultProject >> rootDescriptor
        projectStateRegistry.stateFor(buildId, _) >> Stub(ProjectState)
        projectRegistry.getProject(':') >> rootProject

        when:
        buildLoader.load(settingsInternal, gradle)

        then:
        1 * rootProjectState.createMutableModel(rootProjectClassLoaderScope, baseProjectClassLoaderScope)
        _ * rootProjectState.mutableModel >> rootProject

        and:
        1 * gradle.setRootProject(rootProject)
        1 * gradle.setDefaultProject(rootProject)
    }

    def createsBuildWithMultipleProjectsAndNotRootDefaultProject() {
        given:
        def childProjectState = Mock(ProjectState)
        def childProjectClassLoaderScope = Mock(ClassLoaderScope)
        settingsInternal.defaultProject >> childDescriptor
        projectStateRegistry.stateFor(buildId, Path.path(':child')) >> childProjectState
        projectRegistry.getProject(':child') >> childProject

        when:
        buildLoader.load(settingsInternal, gradle)

        then:
        1 * rootProjectClassLoaderScope.createChild(_) >> childProjectClassLoaderScope
        1 * rootProjectState.mutableModel >> rootProject
        1 * rootProjectState.createMutableModel(rootProjectClassLoaderScope, baseProjectClassLoaderScope)
        1 * childProjectState.createMutableModel(childProjectClassLoaderScope, baseProjectClassLoaderScope)

        and:
        1 * gradle.setRootProject(rootProject)
        1 * gradle.setDefaultProject(childProject)

        and:
        rootProject.childProjects['child'].is childProject
    }

    ProjectDescriptor descriptor(String name, ProjectDescriptor parent, File projectDir) {
        new DefaultProjectDescriptor(parent, name, projectDir, projectDescriptorRegistry, TestFiles.resolver(rootProjectDir))
    }

    ProjectInternal project(ProjectDescriptor descriptor, ProjectInternal parent) {
        ProjectInternal project
        if (parent) {
            project = TestUtil.createChildProject(parent, descriptor.name, descriptor.projectDir)
        } else {
            project = TestUtil.createRootProject(descriptor.projectDir)
        }
        project
    }
}
