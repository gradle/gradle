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

/**
 * Used to log the progress of a potentially long running operation.
 */
public interface ProgressLogger {
    /**
     * Logs the start of the operation.
     *
     * @param description A short description of the long running operation.
     */
    void started(String description);

    /**
     * Logs some progress.
     *
     * @param progressMessage The new status message
     */
    void progress(String progressMessage);

    /**
     * Logs the completion of the operation
     */
    void completed();

    /**
     * Logs the completion of the operation
     *
     * @param status The final status message
     */
    void completed(String status);
}
