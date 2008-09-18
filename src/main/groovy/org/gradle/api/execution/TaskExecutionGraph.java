package org.gradle.api.execution;

import org.gradle.api.Task;
import org.gradle.api.invocation.Build;

import java.util.Set;

/**
 * <p>A <code>TaskExecutionGraph</code> is responsible for managing the ordering and execution of {@link Task}
 * instances. The <code>TaskExecutionGraph</code> maintains an execution plan of tasks to be executed (or which have
 * been executed), which you can query.</p>
 *
 * <p>You can obtain an instance of {@code TaskExecutionGraph} by calling {@link Build#getTaskExecutionGraph()}.</p>
 *
 * <p>The <code>TaskExecutionGraph</code> is populated after all the projects in the build have been evaulated. It is
 * empty before then.</p>
 */
public interface TaskExecutionGraph {
    /**
     * <p>Determines whether the given task is included in the execution plan.</p>
     *
     * @param path the <em>absolute</em> path of the task
     * @return true if a task with the given path is included in the execution plan.
     */
    boolean hasTask(String path);

    /**
     * <p>Returns the set of all tasks which are included in the execution plan.</p>
     *
     * @return The tasks. Returns an empty set if no tasks are to be executed.
     */
    Set<Task> getAllTasks();
}
