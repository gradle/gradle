package org.gradle.api.invocation;

import org.gradle.api.Project;
import org.gradle.api.execution.TaskExecutionGraph;

import java.io.File;

/**
 * <p>A {@code Build} represents an invocation of Gradle.</p>
 *
 * <p>You can obtain a {@code Build} instance by calling {@link Project#getBuild()}</p>
 */
public interface Build {
    /**
     * <p>Returns the current Gradle version.</p>
     *
     * @return The Gradle version. Never returns null.
     */
    String getGradleVersion();

    /**
     * <p>Returns the Gradle user home directory. This directory is used to cache downloaded resources.</p>
     *
     * @return The user home directory. Never returns null.
     */
    File getGradleUserHomeDir();

    /**
     * <p>Returns the Gradle home directory. This directory is used to locate resources such as the default imports
     * file.</p>
     *
     * @return The home directory. Never returns null.
     */
    File getGradleHomeDir();

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
