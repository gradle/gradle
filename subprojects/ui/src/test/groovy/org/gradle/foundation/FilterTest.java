/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.foundation;

import junit.framework.TestCase;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.foundation.visitors.TaskTreePopulationVisitor;
import org.gradle.gradleplugin.foundation.filters.AllowAllProjectAndTaskFilter;
import org.gradle.gradleplugin.foundation.filters.BasicFilterEditor;
import org.gradle.gradleplugin.foundation.filters.ProjectAndTaskFilter;
import org.jmock.integration.junit4.JUnit4Mockery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Test various aspects of filtering out tasks and projects from the GradlePluginLord.
 */
public class FilterTest extends TestCase {
    private BuildInformation buildInformation;

    private ProjectView myRootProject;

    private ProjectView mySubProject1;
    private TaskView mySubProject1Comple;
    private TaskView mySubProject1Lib;
    private TaskView mySubProject1Doc;

    private ProjectView mySubSubProject;
    private TaskView mySubSubProjectCompile;
    private TaskView mySubSubProjectLib;
    private TaskView mySubSubProjectDoc;

    private ProjectView mySubProject2;
    private TaskView mySubProject2Lib;
    private TaskView mySubProject2doc;
    private TaskView mySubProject2Compile;

    protected void setUp() throws Exception {
        JUnit4Mockery context = new JUnit4Mockery();

        Task subsubCompileTask = TestUtility.createTask(context, "compile", "compile description");
        Task subsubLibTask = TestUtility.createTask(context, "lib", "lib description");
        Task subsubDocTask = TestUtility.createTask(context, "doc", "doc description");
        Project subsubProject = TestUtility.createMockProject(context, "mysubsubproject", "filepath3", 2, null,
                new Task[]{subsubCompileTask, subsubLibTask, subsubDocTask}, null, (Project[]) null);

        Task subCompileTask1 = TestUtility.createTask(context, "compile", "compile description");
        Task subLibTask1 = TestUtility.createTask(context, "lib", "lib description");
        Task subDocTask1 = TestUtility.createTask(context, "doc", "doc description");
        Project subProject1 = TestUtility.createMockProject(context, "mysubproject1", "filepath2a", 1,
                new Project[]{subsubProject}, new Task[]{subCompileTask1, subLibTask1, subDocTask1}, null,
                (Project[]) null);

        Task subCompileTask2 = TestUtility.createTask(context, "compile", "compile description");
        Task subLibTask2 = TestUtility.createTask(context, "lib", "lib description");
        Task subDocTask2 = TestUtility.createTask(context, "doc", "doc description");
        Project subProject2 = TestUtility.createMockProject(context, "mysubproject2", "filepath2b", 1, null,
                new Task[]{subCompileTask2, subLibTask2, subDocTask2}, null, (Project[]) null);

        Project rootProject = TestUtility.createMockProject(context, "myrootproject", "filepath1", 0,
                new Project[]{subProject1, subProject2}, null, null, (Project[]) null);

        buildInformation = new BuildInformation(rootProject);

        //now get the converted objects to simplify our matching
        myRootProject = buildInformation.getProjectFromFullPath("myrootproject");
        assertNotNull(myRootProject);
        mySubProject1 = buildInformation.getProjectFromFullPath("myrootproject:mysubproject1");
        assertNotNull(mySubProject1);
        mySubProject1Comple = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:compile");
        assertNotNull(mySubProject1Comple);
        mySubProject1Lib = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:lib");
        assertNotNull(mySubProject1Lib);
        mySubProject1Doc = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:doc");
        assertNotNull(mySubProject1Doc);
        mySubSubProject = buildInformation.getProjectFromFullPath("myrootproject:mysubproject1:mysubsubproject");
        assertNotNull(mySubSubProject);
        mySubSubProjectCompile = buildInformation.getTaskFromFullPath(
                "myrootproject:mysubproject1:mysubsubproject:compile");
        assertNotNull(mySubSubProjectCompile);
        mySubSubProjectLib = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:mysubsubproject:lib");
        assertNotNull(mySubSubProjectLib);
        mySubSubProjectDoc = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:mysubsubproject:doc");
        assertNotNull(mySubSubProjectDoc);
        mySubProject2 = buildInformation.getProjectFromFullPath("myrootproject:mysubproject2");
        assertNotNull(mySubProject2);
        mySubProject2Compile = buildInformation.getTaskFromFullPath("myrootproject:mysubproject2:compile");
        assertNotNull(mySubProject2Compile);
        mySubProject2Lib = buildInformation.getTaskFromFullPath("myrootproject:mysubproject2:lib");
        assertNotNull(mySubProject2Lib);
        mySubProject2doc = buildInformation.getTaskFromFullPath("myrootproject:mysubproject2:doc");
        assertNotNull(mySubProject2doc);
    }

    /**
     * This tests the 'allow all' filter. We just want to make sure it doesn't filter out anything. This also verifies
     * the project and task visitor works.
     */
    public void testAllowAllFiltering() {
        TestVisitor testVisitor = new TestVisitor();

        TaskTreePopulationVisitor.visitProjectAndTasks(buildInformation.getProjects(), testVisitor,
                new AllowAllProjectAndTaskFilter(), null);

        //everything should show up
        testVisitor.setExpectedProjects(myRootProject, mySubProject1, mySubSubProject, mySubProject2);
        testVisitor.setExpectedTasks(mySubProject1Lib, mySubProject1Doc, mySubSubProjectLib, mySubSubProjectDoc,
                mySubProject2Lib, mySubProject2doc, mySubProject1Comple, mySubSubProjectCompile, mySubProject2Compile);
        testVisitor.verifyResults();
    }

    /**
     * This tests filtering out a task. We should see all other tasks. We want to also verify that the task is filtered
     * out at all levels (projects and sub projects).
     */
    public void testTaskFiltering() {
        //filter out tasks named 'lib'
        BasicFilterEditor editor = new BasicFilterEditor();
        editor.hideTasksByName("lib");
        ProjectAndTaskFilter filter = editor.createFilter();

        TestVisitor testVisitor = new TestVisitor();
        TaskTreePopulationVisitor.visitProjectAndTasks(buildInformation.getProjects(), testVisitor, filter, null);

        testVisitor.setExpectedProjects(myRootProject, mySubProject1, mySubSubProject, mySubProject2);
        testVisitor.setExpectedTasks(mySubProject1Doc, mySubSubProjectDoc, mySubProject2doc, mySubProject1Comple,
                mySubSubProjectCompile, mySubProject2Compile);
        testVisitor.verifyResults();
    }

    /**
     * This visitor records the task and projects that it finds and upon calling verifyResults() compares it to what was
     * expected.
     */
    private class TestVisitor implements TaskTreePopulationVisitor.Visitor<Object, Object> {
        private List<TaskView> expectedTasks;
        private List<ProjectView> expectedProjects;

        private List<TaskView> foundTasks = new ArrayList<TaskView>();
        private List<ProjectView> foundProjects = new ArrayList<ProjectView>();

        public void setExpectedProjects(ProjectView... expectedProjectsFullName) {
            this.expectedProjects = Arrays.asList(expectedProjectsFullName);
        }

        public void setExpectedTasks(TaskView... expectedTasksFullName) {
            this.expectedTasks = Arrays.asList(expectedTasksFullName);
        }

        public Object visitProject(ProjectView project, int indexOfProject, Object parentProjectObject) {
            foundProjects.add(project);

            return null;
        }

        public Object visitTask(TaskView task, int indexOfTask, ProjectView tasksProject, Object userProjectObject) {
            foundTasks.add(task);

            return null;
        }

        public void completedVisitingProject(Object parentProjectObject, List<Object> projectObjects,
                                             List<Object> taskObjects) {
        }

        /**
         * Call this after visiting all projects and tasks to verify that we found what we expected.
         */
        public void verifyResults() {
            TestUtility.assertListContents(foundProjects, expectedProjects);
            TestUtility.assertListContents(foundTasks, expectedTasks);
        }
    }

    /**
     * This tests filtering out a project. We expect all tasks, sub projects and sub project's tasks to be hidden.
     */
    public void testProjectFiltering() {
        BasicFilterEditor editor = new BasicFilterEditor();
        editor.hideProjectsByName("mysubproject1");
        ProjectAndTaskFilter filter = editor.createFilter();

        TestVisitor testVisitor = new TestVisitor();

        TaskTreePopulationVisitor.visitProjectAndTasks(buildInformation.getProjects(), testVisitor, filter, null);

        testVisitor.setExpectedProjects(myRootProject, mySubProject2);

        //none of the tasks from my sub project1 or my sub sub project will show up
        testVisitor.setExpectedTasks(mySubProject2Lib, mySubProject2doc, mySubProject2Compile);

        testVisitor.verifyResults();
    }
}
