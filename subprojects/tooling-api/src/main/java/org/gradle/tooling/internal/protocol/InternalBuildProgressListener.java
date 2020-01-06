/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.protocol;

import java.util.List;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 */
public interface InternalBuildProgressListener {

    /**
     * The constant for the test execution operations.
     */
    String TEST_EXECUTION = "TEST_EXECUTION";

    /**
     * The constant for the task execution operations.
     */
    String TASK_EXECUTION = "TASK_EXECUTION";

    /**
     * The constant for the build execution operations.
     */
    String BUILD_EXECUTION = "BUILD_EXECUTION";

    /**
     * The constant for the work item execution operations.
     */
    String WORK_ITEM_EXECUTION = "WORK_ITEM_EXECUTION";

    /**
     * The constant for the project configuration operations.
     */
    String PROJECT_CONFIGURATION_EXECUTION = "PROJECT_CONFIGURATION_EXECUTION";

    /**
     * The constant for the transform operations.
     */
    String TRANSFORM_EXECUTION = "TRANSFORM_EXECUTION";

    /**
     * The constant for the test output of the task execution operations.
     */
    String TEST_OUTPUT = "TEST_OUTPUT";

    /**
     * Invoked when a progress event happens in the build being run, and one or more listeners for the given event type have been registered.
     *
     * The event types implemented in Gradle 2.4 are:
     *
     * <ul>
     *     <li>{@link org.gradle.tooling.internal.protocol.events.InternalTestProgressEvent}</li>
     * </ul>
     *
     * The event types implemented in Gradle 2.5 are:
     *
     * <ul>
     *     <li>{@link org.gradle.tooling.internal.protocol.events.InternalProgressEvent} - used for all operation types.</li>
     *     <li>{@link org.gradle.tooling.internal.protocol.events.InternalTestProgressEvent} - test events also implement these types for backwards compatibility</li>
     * </ul>
     *
     * @param event The issued progress event
     */
    void onEvent(Object event);

    /**
     * Returns the type of operations that the listener wants to subscribe to.
     *
     * @return the type of operations to be notified about
     */
    List<String> getSubscribedOperations();

}
