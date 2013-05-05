/*
 * Copyright 2010 the original author or authors.
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

import org.junit.Assert;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol;
import org.gradle.gradleplugin.foundation.DOM4JSerializer;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.request.ExecutionRequest;
import org.gradle.gradleplugin.foundation.request.RefreshTaskListRequest;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.internal.UncheckedException;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;

import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class for initializing various test objects related.
 *
 * @author mhunsicker
 */
public class TestUtility {
    private static long uniqueNameCounter = 1; //used to make unique names for JMock objects.

    /**
     * Creates a mock project with the specified properties.
     *
     * Note: depth is 0 for a root project. 1 for a root project's subproject, etc.
     */
    public static Project createMockProject(JUnit4Mockery context, final String name, final String buildFilePath, final int depth, Project[] subProjectArray, Task[] tasks, String[] defaultTasks,
                                            Project... dependsOnProjects) {
        final Project project = context.mock(Project.class, "[project]_" + name + '_' + uniqueNameCounter++);

        context.checking(new Expectations() {{
            allowing(project).getName();
            will(returnValue(name));
            allowing(project).getDescription();
            will(returnValue(null));
            allowing(project).getBuildFile();
            will(returnValue(new File(buildFilePath)));
            allowing(project).getDepth();
            will(returnValue(depth));
        }});

        attachSubProjects(context, project, subProjectArray);
        attachTasks(context, project, tasks);
        assignDefaultTasks(context, project, defaultTasks);
        assignDependsOnProjects(context, project, dependsOnProjects);

        return project;
    }

    /**
     * This makes the sub projects children of the parent project. If you call this repeatedly on the same parentProject, any previous sub projects will be replaced with the new ones.
     *
     * @param context the mock context
     * @param parentProject where to attach the sub projects. This must be a mock object.
     * @param subProjectArray the sub projects to attach to the parent. These must be mock objects. Pass in null or an empty array to set no sub projects.
     */
    public static void attachSubProjects(JUnit4Mockery context, final Project parentProject, Project... subProjectArray) {
        final Map<String, Project> childProjects = new LinkedHashMap<String, Project>();
        if (subProjectArray != null) {
            for (final Project subProject : subProjectArray) {
                childProjects.put(String.valueOf(childProjects.size()), subProject);
                context.checking(new Expectations() {{
                    allowing(subProject).getParent();
                    will(returnValue(parentProject));
                }});
            }
        }

        //populate the subprojects (this may be an empty set)
        context.checking(new Expectations() {{
            allowing(parentProject).getChildProjects();
            will(returnValue(childProjects));
        }});
    }

    /**
     * Creates a mock task with the specified properites.
     */
    public static Task createTask(JUnit4Mockery context, final String name, final String description) {
        final Task task = context.mock(Task.class, "[task]_" + name + '_' + uniqueNameCounter++);

        context.checking(new Expectations() {{
            allowing(task).getName();
            will(returnValue(name));
            allowing(task).getDescription();
            will(returnValue(description));
        }});

        return task;
    }

    /**
     * This makes the tasks children of the parent project. If you call this repeatedly on the same parentProject, any previous tasks will be replaced with the new ones.
     *
     * @param context the mock context
     * @param parentProject where to attach the sub projects. This must be a mock object.
     * @param taskArray the tasks to attach to the parent. these must be mock objects. Pass in null or an empty array to set no tasks.
     */
    public static void attachTasks(JUnit4Mockery context, final Project parentProject, Task... taskArray) {
        //first, make our project return our task container
        final TaskContainer taskContainer = context.mock(TaskContainer.class, "[taskcontainer]_" + parentProject.getName() + '_' + uniqueNameCounter++);

        context.checking(new Expectations() {{
            allowing(parentProject).getTasks();
            will(returnValue(taskContainer));
        }});

        final Set<Task> set
                = new LinkedHashSet<Task>();   //using a LinkedHashSet rather than TreeSet (which is what gradle uses) so I don't have to deal with compareTo() being called on mock objects.

        if (taskArray != null && taskArray.length != 0) {
            set.addAll(Arrays.asList(taskArray));

            //set the parent project of the tasks
            for (int index = 0; index < taskArray.length; index++) {
                final Task task = taskArray[index];

                context.checking(new Expectations() {{
                    allowing(task).getProject();
                    will(returnValue(parentProject));
                }});
            }
        }

        //populate the task container (this may be an empty set)
        context.checking(new Expectations() {{
            allowing(taskContainer).iterator();
            will(returnIterator(set));
        }});
    }

    private static void assignDefaultTasks(JUnit4Mockery context, final Project project, final String... defaultTasksArray) {
        final List<String> defaultTaskList = new ArrayList<String>();

        if (defaultTasksArray != null && defaultTasksArray.length != 0) {
            defaultTaskList.addAll(Arrays.asList(defaultTasksArray));
        }

        context.checking(new Expectations() {{
            allowing(project).getDefaultTasks();
            will(returnValue(defaultTaskList));
        }});
    }

    private static void assignDependsOnProjects(JUnit4Mockery context, final Project project, final Project... dependsOnProjects) {
        final Set<Project> set
                = new LinkedHashSet<Project>();   //using a LinkedHashSet rather than TreeSet (which is what gradle uses) so I don't have to deal with compareTo() being called on mock objects.

        if (dependsOnProjects != null && dependsOnProjects.length != 0) {
            set.addAll(Arrays.asList(dependsOnProjects));
        }

        //populate the subprojects (this may be an empty set)
        context.checking(new Expectations() {{
            allowing(project).getDependsOnProjects();
            will(returnValue(set));
        }});
    }

    public static <T> void assertListContents(List<T> actualObjects, T... expectedObjectsArray) {
        assertListContents(actualObjects, Arrays.asList(expectedObjectsArray));
    }

    public static <T> void assertListContents(List<T> actualObjects, List<T> expectedObjects) {
        assertUnorderedListContents(actualObjects, expectedObjects);
    }

    /**
     * This asserts the contents of the list are as expected. The important aspect of this function is that we don't care about ordering. We just want to make sure the contents are the same.
     *
     * @param actualObjecs the list to check
     * @param expectedObjects what we expect in the list
     */
    public static <T> void assertUnorderedListContents(List<T> actualObjecs, List<T> expectedObjects) {
        List<T> expectedObjecsList = new ArrayList<T>(expectedObjects);   //make a copy of it, so we can modify it.

        while (!expectedObjecsList.isEmpty()) {
            T expectedObject = expectedObjecsList.remove(0);

            if (!actualObjecs.contains(expectedObject)) {
                throw new AssertionError("Failed to locate object. Sought object:\n" + expectedObject + "\n\nExpected:\n" + dumpList(expectedObjects) + "\nActual:\n" + dumpList(actualObjecs));
            }
        }

        if (actualObjecs.size() != expectedObjects.size()) {
            throw new AssertionError("Expected " + expectedObjects.size() + " items but found " + actualObjecs.size() + "\nExpected:\n" + dumpList(expectedObjects) + "\nActual:\n" + dumpList(
                    actualObjecs));
        }
    }

    //function for getting a prettier dump of a list.

    public static String dumpList(List list) {
        if (list == null) {
            return "[null]";
        }
        if (list.isEmpty()) {
            return "[empty]";
        }

        StringBuilder builder = new StringBuilder();
        Iterator iterator = list.iterator();
        while (iterator.hasNext()) {
            Object object = iterator.next();
            if (object == null) {
                builder.append("**** [null object in list] ****\n");
            } else {
                builder.append(object.toString()).append('\n');
            }
        }

        return builder.toString();
    }

    /**
     * This is an ExportInteraction implemention meant to be used by tests. You pass it a file to use and we'll return that in promptForFile. This also checks to ensure something doesn't happen where
     * we get into an endless loop if promptForFile is called repeatedly. This can happen if promptForFile is called and its return value fails some form of validation which makes promptForFile get
     * called again or if you deny overwriting the file. You'll get prompted again.
     */
    public static class TestExportInteraction implements DOM4JSerializer.ExportInteraction {
        private File file;
        private boolean confirmOverwrite;
        private int promptCount;

        public TestExportInteraction(File file, boolean confirmOverwrite) {
            this.file = file;
            this.confirmOverwrite = confirmOverwrite;
        }

        public File promptForFile(FileFilter fileFilters) {
            if (promptCount == 100) {
                throw new AssertionError("Possible endless loop. PromptForFile has been called 100 times.");
            }

            promptCount++;
            return file;
        }

        /**
         * The file already exists. Confirm whether or not you want to overwrite it.
         *
         * @param file the file in question
         * @return true to overwrite it, false not to.
         */
        public boolean confirmOverwritingExistingFile(File file) {
            return confirmOverwrite;
        }

        public void reportError(String error) {
            throw new AssertionError("Unexpected error: " + error);
        }
    }

    /**
     * This is an ImportInteraction implementation meant to be used by tests. See TestExportInteraction for more information.
     */
    public static class TestImportInteraction implements DOM4JSerializer.ImportInteraction {
        private File file;
        private int promptCount;

        public TestImportInteraction(File file) {
            this.file = file;
        }

        public File promptForFile(FileFilter fileFilters) {
            if (promptCount == 100) {
                throw new AssertionError("Possible endless loop. PromptForFile has been called 100 times.");
            }

            promptCount++;
            return file;
        }

        public void reportError(String error) {
            throw new AssertionError("Unexpected error: " + error);
        }
    }

    //wrapper around File.createTempFile just so we don't have to deal with the exception for tests.

    /**
     * This refreshes the projects but blocks until it is complete (its being executed in a separate process).
     *
     * @param gradlePluginLord the plugin lord (will be used to execute the command and store the results).
     */
    public static void refreshProjectsBlocking(GradlePluginLord gradlePluginLord, int maximumWaitValue, TimeUnit maximumWaitUnits) {
        refreshProjectsBlocking(gradlePluginLord, new ExecuteGradleCommandServerProtocol.ExecutionInteraction() {
            public void reportExecutionStarted() {
            }

            public void reportNumberOfTasksToExecute(int size) {
            }

            public void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable) {
            }

            public void reportTaskStarted(String message, float percentComplete) {
            }

            public void reportTaskComplete(String message, float percentComplete) {
            }

            public void reportLiveOutput(String message) {
            }
        }, maximumWaitValue, maximumWaitUnits);
    }

    private static void refreshProjectsBlocking(GradlePluginLord gradlePluginLord, final ExecuteGradleCommandServerProtocol.ExecutionInteraction executionInteraction, int maximumWaitValue,
                                                TimeUnit maximumWaitUnits) {
        gradlePluginLord.startExecutionQueue();   //make sure its started

        final CountDownLatch complete = new CountDownLatch(1);
        final AtomicReference<String> errorOutput = new AtomicReference<String>();

        GradlePluginLord.RequestObserver observer = new GradlePluginLord.RequestObserver() {
            public void executionRequestAdded(ExecutionRequest request) {
            }

            public void refreshRequestAdded(RefreshTaskListRequest request) {
                request.setExecutionInteraction(executionInteraction);
            }

            public void aboutToExecuteRequest(Request request) {
            }

            public void requestExecutionComplete(Request request, int result, String output) {
                if (result != 0) {
                    errorOutput.set(output);
                }
                complete.countDown();
            }
        };

        gradlePluginLord.addRequestObserver(observer, false);   //add the observer before we add the request due to timing issues. It's possible for it to completely execute before we return from addRefreshRequestToQueue.
        Request request = gradlePluginLord.addRefreshRequestToQueue();

        //make sure we've got a request
        Assert.assertNotNull(request);

        //now wait until we're complete, but bail if we wait too long
        boolean completed;
        try {
            completed = complete.await(maximumWaitValue, maximumWaitUnits);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        gradlePluginLord.removeRequestObserver(observer);

        if (!completed) {
            //its still running. Something is wrong.
            request.cancel(); //just to clean up after ourselves a little, cancel the request.
            throw new AssertionError("Failed to complete refresh in alotted time: " + maximumWaitValue + " " + maximumWaitUnits + ". Considering this failed.");
        }
        if (errorOutput.get() != null) {
            throw new AssertionError(String.format("Command failed with output:%n%s", errorOutput.get()));
        }
    }

    /**
     * This executes a command and waits until it is finished.
     *
     * @param gradlePluginLord the plugin lord
     * @param fullCommandLine the command to execute
     * @param displayName the display name of the command. It doesn't usuall matter.
     * @param executionInteraction this gets the results of the execution
     * @param maximumWaitSeconds the maximum time to wait before considering it failed.
     */
    public static void executeBlocking(GradlePluginLord gradlePluginLord, String fullCommandLine, String displayName,
                                       final ExecuteGradleCommandServerProtocol.ExecutionInteraction executionInteraction, int maximumWaitSeconds) {
        gradlePluginLord.startExecutionQueue();   //make sure its started

        final CountDownLatch complete = new CountDownLatch(1);

        GradlePluginLord.RequestObserver observer = new GradlePluginLord.RequestObserver() {
            public void executionRequestAdded(ExecutionRequest request) {
                request.setExecutionInteraction(executionInteraction);
            }

            public void refreshRequestAdded(RefreshTaskListRequest request) {
            }

            public void aboutToExecuteRequest(Request request) {
            }

            public void requestExecutionComplete(Request request, int result, String output) {
                complete.countDown();
            }
        };

        gradlePluginLord.addRequestObserver(observer,
                false);   //add the observer before we add the request due to timing issues. It's possible for it to completely execute before we return from addExecutionRequestToQueue.
        Request request = gradlePluginLord.addExecutionRequestToQueue(fullCommandLine, displayName);

        //make sure we've got a request
        Assert.assertNotNull(request);

        //now sleep until we're complete, but bail if we wait too long
        boolean timeout;
        try {
            timeout = !complete.await(maximumWaitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        gradlePluginLord.removeRequestObserver(observer);

        if (timeout) {
            //its still running. Something is wrong.
            request.cancel(); //just to clean up after ourselves a little, cancel the request.
            throw new AssertionError("Failed to comlete execution in alotted time: " + maximumWaitSeconds + " seconds. Considering this failed.");
        }
    }
}

