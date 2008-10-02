package org.gradle.api.execution;

/**
 * <p>A {@code TaskExecutionGraphListener} is notified when the {@link TaskExecutionGraph} has been populated. You can
 * use this interface in your build file to perform some action based on the contents of the graph, before any tasks are
 * actually executed.</p>
 */
public interface TaskExecutionGraphListener {
    /**
     * <p>This method is called when the {@link TaskExecutionGraph} has been populated, and before any tasks are
     * executed.
     *
     * @param graph The graph. Never null.
     */
    void graphPrepared(TaskExecutionGraph graph);
}
