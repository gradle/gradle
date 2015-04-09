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
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.IProjectFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
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
    StartParameter startParameter = new StartParameter()
    ProjectDescriptor rootDescriptor
    ProjectInternal rootProject
    ProjectDescriptor childDescriptor
    ProjectInternal childProject
    GradleInternal build
    def rootProjectClassLoaderScope = Mock(ClassLoaderScope)
    def baseClassLoaderScope = Mock(ClassLoaderScope) {
        1 * createChild("root-project") >> rootProjectClassLoaderScope
    }

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    def setup() {
        projectFactory = Mock(IProjectFactory)
        buildLoader = new InstantiatingBuildLoader(projectFactory)
        testDir = tmpDir.testDirectory
        (rootProjectDir = new File(testDir, 'root')).mkdirs()
        (childProjectDir = new File(rootProjectDir, 'child')).mkdirs()
        startParameter.currentDir = rootProjectDir
        rootDescriptor = descriptor('root', null, rootProjectDir)
        rootProject = project(rootDescriptor, null)
        childDescriptor = descriptor('child', rootDescriptor, childProjectDir)
        childProject = project(childDescriptor, rootProject)
        build = Mock(GradleInternal)
        build.getStartParameter() >> startParameter
    }

    def createsBuildWithRootProjectAsTheDefaultOne() {
        when:
        ProjectDescriptor rootDescriptor = descriptor('root', null, rootProjectDir)
        ProjectInternal rootProject = project(rootDescriptor, null)

        projectFactory.createProject(rootDescriptor, null, !null, rootProjectClassLoaderScope, baseClassLoaderScope) >> rootProject
        1 * build.setRootProject(rootProject)
        build.getRootProject() >> rootProject
        1 * build.setDefaultProject(rootProject)

        then:
        buildLoader.load(rootDescriptor, rootDescriptor, build, baseClassLoaderScope)
    }

    def createsBuildWithMultipleProjectsAndNotRootDefaultProject() {
        when:
        expectProjectsCreated()
        1 * build.setDefaultProject(childProject)
        buildLoader.load(rootDescriptor, childDescriptor, build, baseClassLoaderScope)

        then:
        rootProject.childProjects['child'].is childProject
    }

    def expectProjectsCreated() {
        1 * projectFactory.createProject(rootDescriptor, null, !null, rootProjectClassLoaderScope, baseClassLoaderScope) >> rootProject
        1 * projectFactory.createProject(childDescriptor, rootProject, !null, _ as ClassLoaderScope, baseClassLoaderScope) >> childProject
        1 * build.setRootProject(rootProject)
        build.getRootProject() >> rootProject
    }

    ProjectDescriptor descriptor(String name, ProjectDescriptor parent, File projectDir) {
        new DefaultProjectDescriptor(parent, name, projectDir, projectDescriptorRegistry, TestFiles.resolver(rootProjectDir))
    }

    ProjectInternal project(ProjectDescriptor descriptor, ProjectInternal parent) {
        DefaultProject project
        if (parent) {
            project = TestUtil.createChildProject(parent, descriptor.name, descriptor.projectDir)
        } else {
            project = TestUtil.createRootProject(descriptor.projectDir)
        }
        project
    }
}
