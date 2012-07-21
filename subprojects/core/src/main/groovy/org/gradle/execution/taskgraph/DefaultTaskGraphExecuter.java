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

package org.gradle.execution.taskgraph;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactStateCacheAccess;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.execution.TaskFailureHandler;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
import org.gradle.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultTaskGraphExecuter implements TaskGraphExecuter {
    private static Logger logger = LoggerFactory.getLogger(DefaultTaskGraphExecuter.class);

    private final TaskArtifactStateCacheAccess cacheAccess;

    private final ListenerBroadcast<TaskExecutionGraphListener> graphListeners;
    private final ListenerBroadcast<TaskExecutionListener> taskListeners;
    private final TaskExecutionPlan taskExecutionPlan = new TaskExecutionPlan();
    private boolean populated;

    public DefaultTaskGraphExecuter(ListenerManager listenerManager, TaskArtifactStateCacheAccess cacheAccess) {
        graphListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class);
        taskListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class);
        this.cacheAccess = cacheAccess;
    }

    public void useFilter(Spec<? super Task> filter) {
        taskExecutionPlan.useFilter(filter);
    }

    public void useFailureHandler(TaskFailureHandler handler) {
        taskExecutionPlan.useFailureHandler(handler);
    }

    public void addTasks(Iterable<? extends Task> tasks) {
        assert tasks != null;

        Clock clock = new Clock();

        Set<Task> taskSet = new LinkedHashSet<Task>();
        for (Task task : tasks) {
            taskSet.add(task);
        }
        taskExecutionPlan.addToTaskGraph(taskSet);
        populated = true;

        logger.debug("Timing: Creating the DAG took " + clock.getTime());
    }

    public void execute() {
        assertPopulated();
        Clock clock = new Clock();

        graphListeners.getSource().graphPopulated(this);

        TaskExecutor taskExecutor = new DefaultTaskExecutor();
        try {
            taskExecutor.process(taskExecutionPlan);
            logger.debug("Timing: Executing the DAG took " + clock.getTime());
        } finally {
            taskExecutionPlan.clear();
        }
    }

    public void execute(Iterable<? extends Task> tasks) {
        addTasks(tasks);
        execute();
    }

    public void addTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.add(listener);
    }

    public void removeTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.remove(listener);
    }

    public void whenReady(final Closure closure) {
        graphListeners.add("graphPopulated", closure);
    }

    public void addTaskExecutionListener(TaskExecutionListener listener) {
        taskListeners.add(listener);
    }

    public void removeTaskExecutionListener(TaskExecutionListener listener) {
        taskListeners.remove(listener);
    }

    public void beforeTask(final Closure closure) {
        taskListeners.add("beforeExecute", closure);
    }

    public void afterTask(final Closure closure) {
        taskListeners.add("afterExecute", closure);
    }

    private void executeTask(TaskInfo taskInfo) {
        TaskInternal task = taskInfo.getTask();
        for (TaskInfo dependency : taskInfo.getDependencies()) {
            if (!dependency.isComplete()) {
                // Cannot execute this task, as some dependencies have not been executed
                String message = String.format("Cannot execute %s, as dependency %s has not been executed", task.getPath(), dependency.getTask().getPath());
                // TODO:DAZ This should not be warning
                logger.warn(message);
                return;
            }
        }

        taskListeners.getSource().beforeExecute(task);
        try {
            task.executeWithoutThrowingTaskFailure();
        } finally {
            taskListeners.getSource().afterExecute(task, task.getState());
        }

        if (task.getState().getFailure() != null) {
            // TODO Not sure if we play well with --continue
            taskExecutionPlan.taskFailed(taskInfo);
        } else {
            taskExecutionPlan.taskComplete(taskInfo);
        }
    }

    public boolean hasTask(Task task) {
        assertPopulated();
        return taskExecutionPlan.hasTask(task);
    }

    public boolean hasTask(String path) {
        assertPopulated();
        assert path != null && path.length() > 0;
        for (Task task : getAllTasks()) {
            if (task.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    public List<Task> getAllTasks() {
        assertPopulated();
        return taskExecutionPlan.getTasks();
    }

    private void assertPopulated() {
        if (!populated) {
            throw new IllegalStateException(
                    "Task information is not available, as this task execution graph has not been populated.");
        }
    }

    private class DefaultTaskExecutor implements TaskExecutor {
        public void process(TaskExecutionPlan taskExecutionPlan) {
            Spec<TaskInfo> anyTask = Specs.satisfyAll();
            TaskInfo toExecute = taskExecutionPlan.getTaskToExecute(anyTask);
            while (toExecute != null) {
                executeTask(toExecute);
                toExecute = taskExecutionPlan.getTaskToExecute(anyTask);
            }
            taskExecutionPlan.awaitCompletion();
        }
    }

    private class ParallelTaskExecutor implements TaskExecutor {
        private final int EXECUTOR_COUNT = 2;
        List<Thread> executorThreads = new ArrayList<Thread>();
        private final TaskArtifactStateCacheAccess stateCacheAccess;

        private ParallelTaskExecutor(TaskArtifactStateCacheAccess cacheAccess) {
            this.stateCacheAccess = cacheAccess;
        }

        public void process(final TaskExecutionPlan taskExecutionPlan) {
            stateCacheAccess.longRunningOperation("Executing all tasks", new Runnable() {
                public void run() {
                    doProcess(taskExecutionPlan);
                    // TODO This needs to wait until all tasks have been executed, not just started....
                    taskExecutionPlan.awaitCompletion();
                }
            });
        }

        private void doProcess(TaskExecutionPlan taskExecutionPlan) {
            List<Project> projects = getAllProjects(taskExecutionPlan);
            int numExecutors = Math.min(EXECUTOR_COUNT, projects.size());

            for (int i = 0; i < numExecutors; i++) {
                TaskExecutorWorker worker = new TaskExecutorWorker(taskExecutionPlan);

                for (int j = i; j < projects.size(); j += numExecutors) {
                    worker.addProject(projects.get(j));
                }

                executorThreads.add(new Thread(worker));
            }

            for (Thread executorThread : executorThreads) {
                // TODO A bunch more stuff to contextualise the thread
                executorThread.start();
            }
        }

        private List<Project> getAllProjects(TaskExecutionPlan taskExecutionPlan) {
            final Set<Project> uniqueProjects = new LinkedHashSet<Project>();
            for (Task task : taskExecutionPlan.getTasks()) {
                uniqueProjects.add(task.getProject());
            }
            return new ArrayList<Project>(uniqueProjects);
        }

        private class TaskExecutorWorker implements Runnable {
           private final TaskExecutionPlan taskExecutionPlan;

           private final List<Project> projects = new ArrayList<Project>();

           private TaskExecutorWorker(TaskExecutionPlan taskExecutionPlan) {
               this.taskExecutionPlan = taskExecutionPlan;
           }

           public void run() {
               TaskInfo taskInfo;
               while ((taskInfo = taskExecutionPlan.getTaskToExecute(getTaskSpec())) != null) {
                   logger.warn("Got task to execute: " + taskInfo.getTask().getPath());
                   executeTaskWithCacheLock(taskInfo);
                   logger.warn("Executed: " + taskInfo.getTask().getPath());
               }
           }

           private void executeTaskWithCacheLock(final TaskInfo taskInfo) {
               final String taskPath = taskInfo.getTask().getPath();
               logger.warn(taskPath + " (" + Thread.currentThread() + " - start");
               stateCacheAccess.useCache("Executing " + taskPath, new Runnable() {
                   public void run() {
                       logger.warn(taskPath + " (" + Thread.currentThread() + ") - have cache: executing");
                       executeTask(taskInfo);
                       logger.warn(taskPath + " (" + Thread.currentThread() + ") - execute done: releasing cache");
                   }
               });
               logger.warn(taskPath + " (" + Thread.currentThread() + ") - complete");
           }

           public void addProject(Project project) {
               projects.add(project);
           }

           private Spec<TaskInfo> getTaskSpec() {
               return new Spec<TaskInfo>() {
                   public boolean isSatisfiedBy(TaskInfo element) {
                       return projects.contains(element.getTask().getProject());
                   }
               };
           }
       }
    }
}
