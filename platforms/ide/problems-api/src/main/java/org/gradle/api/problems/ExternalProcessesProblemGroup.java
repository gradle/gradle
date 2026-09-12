/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * The predefined {@code External Processes} root problem group.
 * <p>
 * External process invocations of compiled applications, native tools, and other runnable artifacts.
 * <p>
 * The predefined sub-groups are available as properties. Further sub-groups can be created with {@link #group(String)}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class ExternalProcessesProblemGroup extends RootProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected ExternalProcessesProblemGroup() {
    }

    /**
     * The {@code Application} sub-group.
     * <p>
     * Execution of compiled applications, including startup, runtime behavior, and exit status.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getApplication();

    /**
     * The {@code Service} sub-group.
     * <p>
     * Lifecycle management of service processes during the build, including startup, shutdown, and health checks.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getService();

    /**
     * The {@code Tool} sub-group.
     * <p>
     * Invocation of external tools as part of the build, including tool discovery and execution.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getTool();
}
