/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

/**
 * An input property value may implement this interface to be notified when the task that owns it starts and completes execution.
 */
public interface LifecycleAwareValue {

    /**
     * Called immediately prior to this property being used as an input.
     * The property implementation may finalize the property value, prevent further changes to the value and enable caching of whatever state it requires to efficiently snapshot and query the input files during execution.
     */
    void prepareValue();

    /**
     * Called after the completion of the unit of work, regardless of the outcome. The property implementation can release any state that was cached during execution.
     */
    void cleanupValue();
}
