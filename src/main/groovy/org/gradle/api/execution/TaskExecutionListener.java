package org.gradle.api.execution;

import org.gradle.api.Task;

/**
 * <p>A {@code TaskExecutionListener} is notified of the execution of the tasks in a build.</p>
 *
 * <p>You can add a {@code TaskExecutionListener} to a build using {@link org.gradle.api.execution.TaskExecutionGraph#addTaskExecutionListener}
 */
public interface TaskExecutionListener {
    /**
     * This method is called immediately before a task is executed.
     *
     * @param task The task about to be executed. Never null.
     */
    void beforeExecute(Task task);

    /**
     * This method is call immediately after a task has been executed. It is called whether the task completed
     * successfully, or failed with an exception.
     *
     * @param task The task which was executed. Never null.
     * @param failure The exception which was thrown by the task, if any. Null if the task completed successfully.
     */
    void afterExecute(Task task, Throwable failure);
}
