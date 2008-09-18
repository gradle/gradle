package org.gradle.api.invocation;

import org.gradle.api.Project;
import org.gradle.api.execution.TaskExecutionGraph;

/**
 * <p>A {@code Build} represents an invocation of Gradle.</p>
 *
 * <p>You can obtain a {@code Build} instance by calling {@link Project#getBuild()}</p> 
 */
public interface Build {
    /**
     * <p>Returns the root project of this build.</p>
     *
     * @return The root project. Never returns null.
     */
    Project getRootProject();

    /**
     * <p>Returns the {@link TaskExecutionGraph} for this build.</p>
     *
     * @return The task graph. Never returns null.
     */
    TaskExecutionGraph getTaskExecutionGraph();
}
