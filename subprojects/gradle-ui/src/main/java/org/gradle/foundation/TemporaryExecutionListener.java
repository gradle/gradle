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
package org.gradle.foundation;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.ExecutionListener;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.StandardOutputListener;

/**
 * @author mhunsicker
 */
public class TemporaryExecutionListener {

    /**
     * <p>Adds an {@link ExecutionListener} to this Gradle instance. The listener is notified of events which occur
     * during a build. This provides more information than a {@link BuildListener} does and is intended to be used by
     * tools that use gradle (such as continuous integration servers or IDE plugins).</p>
     *
     * @param executionListener The listener to add.
     */
    public static void addExecutionListener(Gradle gradle, ExecutionListener executionListener) {
        gradle.addListener(new DelegatingBuildListener(executionListener));
    }

    /**
     * This listens to the build so we can provide feedback and progress to our caller. This just delegates the work to
     * ExecutionListener. Since the information for the ExecutionListener isn't readily available, this automatically
     * sets up several other listeners.
     */
    private static class DelegatingBuildListener
            implements BuildListener, StandardOutputListener, TaskExecutionGraphListener {
        private DelegatingTaskExecutionListener taskProgressListener;
        private TaskExecutionGraph taskExecutionGraph;
        private ExecutionListener executionListener;
        private StringBuffer allOutputText = new StringBuffer();
                //this is potentially threaded, so use StringBuffer instead of StringBuidler

        private DelegatingBuildListener(ExecutionListener executionListener) {
            this.executionListener = executionListener;
        }

        public synchronized void onOutput(CharSequence output) {
            String text = output.toString();
            allOutputText.append(text);
            executionListener.reportLiveOutput(text);
        }

        public void onError(CharSequence output) {
            onOutput(output);
        }

        /**
         * <p>Called when the build is started.</p>
         *
         * @param build The build which is being started. Never null.
         */
        public void buildStarted(Gradle build) {
            executionListener.reportExecutionStarted();
        }

        /**
         * <p>Called when the build settings have been loaded and evaluated. The settings object is fully configured and
         * is ready to use to load the build projects.</p> <p/> <p>Here, we add a Log4J appender so we can capture
         * gradle's (and Ant, etc.) output.</p>
         *
         * @param settings The settings. Never null.
         */
        public void settingsEvaluated(Settings settings) {

        }

        public void projectsLoaded(Gradle build) {
        }

        public void projectsEvaluated(Gradle build) {
        }

        /**
         * <p>Called when the task graph for the build has been populated. The task graph is fully configured and is
         * ready to use to execute the tasks which make up the build.</p> <p>Here we add a TaskExecutionListener to the
         * task graph that just delegates the task messages to the ExecutionListener</p>
         */
        public void graphPopulated(TaskExecutionGraph taskExecutionGraph) {
            this.taskExecutionGraph = taskExecutionGraph;
            int totalTasksToExecute = taskExecutionGraph.getAllTasks().size();

            taskProgressListener = new DelegatingTaskExecutionListener(totalTasksToExecute, executionListener);
            taskExecutionGraph.addTaskExecutionListener(taskProgressListener);
        }

        /**
         * <p>Called when the build is completed. All selected tasks have been executed.</p> <p>We remove our
         * Log4JAppender as well as our task execution listener. Lastly, we report the build results.</p>
         */
        public void buildFinished(BuildResult buildResult) {
            if (taskExecutionGraph
                    != null) //not sure if this is necessary, but just in case... we'll clean up after ourselves.
            {
                taskExecutionGraph.removeTaskExecutionListener(taskProgressListener);
                taskProgressListener = null;
            }

            boolean wasSuccessful = buildResult.getFailure() == null;
            executionListener.reportExecutionFinished(wasSuccessful, buildResult, allOutputText.toString());
        }
    }

    /**
     * Listener that allows us to listen for progress while executing a task. We delegate this to another listener
     */
    private static class DelegatingTaskExecutionListener implements TaskExecutionListener {
        private float totalTasksToExecute;
        private float totalTasksExecuted;
        private float percentComplete;
        private ExecutionListener executionListener;

        private DelegatingTaskExecutionListener(int totalTasksToExecute, ExecutionListener executionListener) {
            this.executionListener = executionListener;
            this.totalTasksToExecute = (float) totalTasksToExecute;
        }

        public void beforeExecute(Task task) {
            executionListener.reportTaskStarted(task.getProject().getName() + ":" + task.getName(), percentComplete);
        }

        public void afterExecute(Task task, TaskExecutionResult result) {
            totalTasksExecuted++;
            percentComplete = (totalTasksExecuted / totalTasksToExecute) * 100;
            executionListener.reportTaskComplete(task.getProject().getName() + ":" + task.getName(), percentComplete);
        }
    }
}

