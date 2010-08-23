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

package org.gradle.logging;

/**
 * Used to log the progress of a potentially long running operation.
 */
public interface ProgressLogger {
    /**
     * Returns the description of the operation. The description is generally logged at the start of the operation.
     *
     * @return the description, possibly empty.
     */
    String getDescription();

    /**
     * Logs some progress, indicated by a new status.
     *
     * @param status The new status message
     */
    void progress(String status);

    /**
     * Logs the completion of the operation, with no final status
     */
    void completed();

    /**
     * Logs the completion of the operation, with a final status. This is generally logged along with the description.
     *
     * @param status The final status message
     */
    void completed(String status);

    /**
     * Returns the current status of the operation.
     *
     * @return The status, possibly empty.
     */
    String getStatus();
}
