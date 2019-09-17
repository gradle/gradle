/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.execution;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.Task;

/**
 * A shared resource required for the execution of a {@link Task}.
 *
 * Shared resources limit task execution concurrency. A shared resource has a defined number of leases. Tasks may declare that they
 * require 1 or more leases of a shared resource. When Gradle schedules tasks for execution it will ensure that the declared number
 * of leases is never exceeded by concurrently executing tasks. A shared resource with a single lease (the default) acts as an
 * exclusive resource, effectively limiting concurrency of any tasks that use that resource such that only a single one can be
 * executed at any given time.
 *
 * @since 6.0
 */
@Incubating
public interface SharedResource extends Named {

    /**
     * <p>Sets the maximum number of leases available to this resource.</p>
     *
     * @param leases Number of leases.
     *
     * @since 6.0
     */
    void setLeases(int leases);

    /**
     * <p>Returns the maximum number of leases available to this resource.</p>
     *
     * <p>A value of <code>1</code> indicates this is an exclusive resource and only a single task which requires this resource
     * can execute at any given time. This is the default value.</p>
     *
     * @return Number of leases.
     *
     * @since 6.0
     */
    int getLeases();
}
