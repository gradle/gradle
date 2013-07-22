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
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.IProjectFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

@RunWith(JMock.class)
class InstantiatingBuildLoaderTest {

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
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    @Rule public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    @Before public void setUp()  {
        projectFactory = context.mock(IProjectFactory)
        buildLoader = new InstantiatingBuildLoader(projectFactory)
        testDir = tmpDir.testDirectory
        (rootProjectDir = new File(testDir, 'root')).mkdirs()
        (childProjectDir = new File(rootProjectDir, 'child')).mkdirs()
        startParameter.currentDir = rootProjectDir
        rootDescriptor = descriptor('root', null, rootProjectDir)
        rootProject = project(rootDescriptor, null)
        childDescriptor = descriptor('child', rootDescriptor, childProjectDir)
        childProject = project(childDescriptor, rootProject)
        build = context.mock(GradleInternal)
        context.checking {
            allowing(build).getStartParameter()
            will(returnValue(startParameter))
        }
    }

    @Test public void createsBuildWithRootProject() {
        ProjectDescriptor rootDescriptor = descriptor('root', null, rootProjectDir)
        ProjectInternal rootProject = project(rootDescriptor, null)

        context.checking {
            one(projectFactory).createProject(withParam(equalTo(rootDescriptor)),
                    withParam(nullValue()),
                    withParam(notNullValue()))
            will(returnValue(rootProject))
            one(build).setRootProject(rootProject)
            allowing(build).getRootProject()
            will(returnValue(rootProject))
            one(build).setDefaultProject(rootProject)
        }

        buildLoader.load(rootDescriptor, build)
    }

    @Test public void createsBuildWithMultipleProjects() {
        expectProjectsCreated()

        buildLoader.load(rootDescriptor, build)

        assertThat(rootProject.childProjects['child'], sameInstance(childProject))
    }

    private def expectProjectsCreatedNoDefaultProject() {
        context.checking {
            one(projectFactory).createProject(withParam(equalTo(rootDescriptor)),
                    withParam(nullValue()),
                    withParam(notNullValue()))
            will(returnValue(rootProject))

            one(projectFactory).createProject(withParam(equalTo(childDescriptor)),
                    withParam(equalTo(rootProject)),
                    withParam(notNullValue()))
            will(returnValue(childProject))

            one(build).setRootProject(rootProject)
            allowing(build).getRootProject()
            will(returnValue(rootProject))
        }
    }

    private def expectProjectsCreated() {
        expectProjectsCreatedNoDefaultProject()

        context.checking {
            one(build).setDefaultProject(rootProject)
        }
    }

    private ProjectDescriptor descriptor(String name, ProjectDescriptor parent, File projectDir) {
        new DefaultProjectDescriptor(parent, name, projectDir, projectDescriptorRegistry)
    }

    private ProjectInternal project(ProjectDescriptor descriptor, ProjectInternal parent) {
        DefaultProject project
        if (parent) {
            project = HelperUtil.createChildProject(parent, descriptor.name, descriptor.projectDir)
        } else {
            project = HelperUtil.createRootProject(descriptor.projectDir)
        }
        project
    }
}
