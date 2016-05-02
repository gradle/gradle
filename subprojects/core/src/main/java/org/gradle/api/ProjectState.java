/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api;

import org.gradle.internal.HasInternalProtocol;

/**
 * {@code ProjectState} provides information about the execution state of a project.
 */
@HasInternalProtocol
public interface ProjectState {
    /**
     * <p>Returns true if this project has been evaluated.</p>
     *
     * @return true if this project has been evaluated.
     */
    boolean getExecuted();

    /**
     * Returns the exception describing the project failure, if any.
     *
     * @return The exception, or null if project evaluation did not fail.
     */
    Throwable getFailure();

    /**
     * Throws the project failure, if any. Does nothing if the project did not fail.
     */
    void rethrowFailure();
}
