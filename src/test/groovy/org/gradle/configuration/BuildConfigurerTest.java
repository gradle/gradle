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

package org.gradle.configuration;

import org.gradle.api.Project;
import org.gradle.api.ProjectAction;
import org.gradle.api.internal.project.BuildScriptProcessor;
import org.gradle.api.internal.project.ProjectsTraverser;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.SortedMap;
import java.util.TreeMap;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class BuildConfigurerTest {
    BuildConfigurer buildConfigurer;
    ProjectDependencies2TaskResolver projectDependencies2TasksResolver;
    BuildScriptProcessor buildScriptProcessor;
    ProjectTasksPrettyPrinter projectTasksPrettyPrinter;
    Project rootProject;
    boolean evaluatedCalled;
    boolean resolveCalled;
    SortedMap expectedTasksMap;
    boolean expectedRecursive;

    JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp()  {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        projectDependencies2TasksResolver = context.mock(ProjectDependencies2TaskResolver.class);
        projectTasksPrettyPrinter = new ProjectTasksPrettyPrinter();
        buildConfigurer = new BuildConfigurer(projectDependencies2TasksResolver, projectTasksPrettyPrinter);
        buildScriptProcessor = new BuildScriptProcessor();
        resolveCalled = false;
        expectedTasksMap = new TreeMap();
        rootProject = HelperUtil.createRootProject(new File("root"));
        rootProject = context.mock(Project.class);
    }

    private void createExpectations() {
        final ProjectAction testEvaluateAction = new ProjectAction() {
            public void execute(Project project) {
                ;
            }
        };
        buildConfigurer.setProjectEvaluateAction(testEvaluateAction);
        context.checking(new Expectations() {{
            allowing(rootProject).evaluate(); will(returnValue(rootProject));
            allowing(rootProject).getBuildScriptProcessor(); will(returnValue(buildScriptProcessor));
            allowing(rootProject).getAllTasks(expectedRecursive); will(returnValue(expectedTasksMap));
            one(rootProject).allprojects(testEvaluateAction);
            one(projectDependencies2TasksResolver).resolve(with(same(rootProject)));
        }});
    }

    @Test
    public void testBuildConfigurer() {
        assert buildConfigurer.getProjectDependencies2TasksResolver() == projectDependencies2TasksResolver;
        assert buildConfigurer.getProjectTasksPrettyPrinter() == projectTasksPrettyPrinter;
    }

    @Test
    public void testProcess() {
        createExpectations();
        buildConfigurer.process(rootProject);
    }

    @Test
    public void testTaskList() {
        createExpectations();
        final String expectedTasksPrettyText = "someTasksText";
        final ProjectTasksPrettyPrinter projectTasksPrettyPrinterMock = context.mock(ProjectTasksPrettyPrinter.class);
        buildConfigurer.setProjectTasksPrettyPrinter(projectTasksPrettyPrinterMock);
        context.checking(new Expectations() {{
            allowing(projectTasksPrettyPrinterMock).getPrettyText(expectedTasksMap); will(returnValue(expectedTasksPrettyText));
        }});

        assertEquals(expectedTasksPrettyText, buildConfigurer.taskList(rootProject, false, rootProject));
    }

}