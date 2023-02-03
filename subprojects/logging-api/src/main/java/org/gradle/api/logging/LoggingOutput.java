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

package org.gradle.api.logging;

import org.gradle.internal.HasInternalProtocol;

/**
 * Provides access to the output of the Gradle logging system.
 */
@HasInternalProtocol
public interface LoggingOutput {
    /**
     * Adds a listener which receives output written to standard output by the Gradle logging system.
     *
     * @param listener The listener to add.
     */
    void addStandardOutputListener(StandardOutputListener listener);

    /**
     * Removes a listener from standard output.
     *
     * @param listener The listener to remove.
     */
    void removeStandardOutputListener(StandardOutputListener listener);

    /**
     * Adds a listener which receives output written to standard error by the Gradle logging system.
     *
     * @param listener The listener to add.
     */
    void addStandardErrorListener(StandardOutputListener listener);

    /**
     * Removes a listener from standard error.
     *
     * @param listener The listener to remove.
     */
    void removeStandardErrorListener(StandardOutputListener listener);
}
