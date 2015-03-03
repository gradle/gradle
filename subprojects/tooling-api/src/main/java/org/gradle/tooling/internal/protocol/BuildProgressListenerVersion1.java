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

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 */
public interface BuildProgressListenerVersion1 {

    /**
     * Invoked when a progress event happens in the build being run, and one or more listeners for the given event type have been registered.
     *
     * The event types implemented in Gradle 2.4 are: <ul> <li>{@link org.gradle.tooling.internal.protocol.TestProgressEventVersion1}</li> </ul>
     *
     * @param event The issued progress event
     */
    void onEvent(Object event);

}
