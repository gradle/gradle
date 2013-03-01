package org.gradle.api.execution;

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskState;

/**
 * A {@link TaskExecutionListener} adapter class for receiving task execution events.
 *
 * The methods in this class are empty. This class exists as convenience for creating listener objects.
 */
public class TaskExecutionAdapter implements TaskExecutionListener {

    public void beforeExecute(Task task) {}

    public void afterExecute(Task task, TaskState state) {}

}
