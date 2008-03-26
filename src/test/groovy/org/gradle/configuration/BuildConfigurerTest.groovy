/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.configuration

import groovy.mock.interceptor.MockFor
import org.gradle.api.Project
import org.gradle.api.internal.project.BuildScriptProcessor
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectsTraverser

/**
* @author Hans Dockter
*/
class BuildConfigurerTest extends GroovyTestCase {
    BuildConfigurer buildConfigurer
    ProjectDependencies2TasksResolver projectDependencies2TasksResolver
    BuildClasspathLoader buildClasspathLoader
    ProjectsTraverser projectsTraverser
    BuildScriptProcessor buildScriptProcessor
    ProjectTasksPrettyPrinter projectTasksPrettyPrinter
    URLClassLoader classLoader
    DefaultProject rootProject
    boolean evaluatedCalled

    void setUp() {
        projectDependencies2TasksResolver = new ProjectDependencies2TasksResolver()
        buildClasspathLoader = new BuildClasspathLoader()
        projectsTraverser = new ProjectsTraverser()
        projectTasksPrettyPrinter = new ProjectTasksPrettyPrinter()
        buildConfigurer = new BuildConfigurer(projectDependencies2TasksResolver, buildClasspathLoader, projectsTraverser, projectTasksPrettyPrinter)
        buildScriptProcessor = new BuildScriptProcessor()
        classLoader = new URLClassLoader([] as URL[])
        evaluatedCalled = false
        rootProject = [evaluate: {evaluatedCalled = true; null}, getBuildScriptProcessor: { buildScriptProcessor }] as DefaultProject
    }

    void testBuildConfigurer() {
        assert buildConfigurer.projectDependencies2TasksResolver.is(projectDependencies2TasksResolver)
        assert buildConfigurer.buildClasspathLoader.is(buildClasspathLoader)
        assert buildConfigurer.projectsTraverser.is(projectsTraverser)
        assert buildConfigurer.projectTasksPrettyPrinter.is(projectTasksPrettyPrinter)
    }

    void testProcess() {
        Closure execution = { buildConfigurer.process(rootProject, classLoader) }
        checkProcess(execution)
    }

    void testTaskList() {
        boolean expectedRecursive = true
        SortedMap expectedTasksMap = new TreeMap()
        String expectedTasksPrettyText = 'someTasksText'
        Project currentProjectMock = [getAllTasks: {boolean recursive ->
            assertEquals(expectedRecursive, recursive)
            expectedTasksMap
        }] as Project
        Closure execution = { buildConfigurer.taskList(rootProject, true, currentProjectMock, classLoader) }
        buildConfigurer.projectTasksPrettyPrinter = [getPrettyText: { SortedMap tasks ->
            assert tasks.is(expectedTasksMap)    
            expectedTasksPrettyText
        }] as ProjectTasksPrettyPrinter
        assertEquals(expectedTasksPrettyText, checkProcess(execution))
    }

    private def checkProcess(Closure execution) {
        def result = null
        MockFor projectsTraverserMocker = new MockFor(ProjectsTraverser)
        Closure traverseClosure
        projectsTraverserMocker.demand.traverse(1..1) {List projects, Closure closure ->
            assertEquals([rootProject], projects)
            traverseClosure = closure
        }
        MockFor dependencies2TasksResolverMocker = new MockFor(ProjectDependencies2TasksResolver)
        dependencies2TasksResolverMocker.demand.resolve(1..1) {DefaultProject root -> assertSame(rootProject, root)}
        dependencies2TasksResolverMocker.use(projectDependencies2TasksResolver) {
            projectsTraverserMocker.use(projectsTraverser) {
                result = execution()
            }
        }
        traverseClosure(rootProject)
        assertTrue(evaluatedCalled)
        assert buildScriptProcessor.classLoader.is(classLoader)
        result
    }
}