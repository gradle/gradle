/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.interfaces;

/**
 * Represents any failure that can occur during variant selection and
 * contains contextual information specific to that type of failure.
 *
 * @implSpec This interface is meant only to be extended by other interfaces, it should not
 * be implemented directly.  Concrete types implementing this interface should be immutable data classes
 * without nullable fields.  They must also be serializable by the configuration cache.
 */
/* package */ interface ResolutionFailure { // TODO:ResolutionFailure - Consider renaming me to SelectionFailure and let the package reflect the resolution part?
    /**
     * Returns a human-readable description of the requested target, for use
     * primarily in error messages.
     *
     * @return description of the requested target
     */
    String describeRequest();
}
