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
public interface BuildProgressListenerVersion1 {

    /**
     * The constant for the test progress event type.
     */
    String TEST_PROGRESS = "TEST_PROGRESS";

    /**
     * Invoked when a progress event happens in the build being run, and one or more listeners for the given event type have been registered.
     *
     * The event types implemented in Gradle 2.4 are: <ul> <li>{@link org.gradle.tooling.internal.protocol.TestProgressEventVersion1}</li> </ul>
     *
     * @param event The issued progress event
     */
    void onEvent(Object event);

    /**
     * Returns the type of events that the listener wants to subscribe to.
     *
     * @return the type of events to be notified about
     */
    List<String> getSubscribedEvents();

}
