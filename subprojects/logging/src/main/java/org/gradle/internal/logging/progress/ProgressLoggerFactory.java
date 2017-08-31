/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.progress;

import org.gradle.internal.progress.BuildOperationDescriptor;

/**
 * Thread-safe, however the progress logger instances created are not.
 */
public interface ProgressLoggerFactory {
    long ROOT_PROGRESS_OPERATION_ID = 1;

    /**
     * Creates a new long-running operation which has not been started.
     *
     * @param loggerCategory The logger category.
     * @return The progress logger for the operation.
     */
    ProgressLogger newOperation(String loggerCategory);

    /**
     * Creates a new long-running operation which has not been started.
     *
     * @param loggerCategory The logger category.
     * @return The progress logger for the operation.
     */
    ProgressLogger newOperation(Class<?> loggerCategory);

    /**
     * Creates a new long-running operation which has not been started, associated
     * with the given build operation id.
     *
     * @param loggerCategory The logger category.
     * @param buildOperationDescriptor descriptor for the build operation associated with this logger.
     * @return the progress logger for the operation.
     */
    ProgressLogger newOperation(Class<?> loggerCategory, BuildOperationDescriptor buildOperationDescriptor);

    ProgressLogger newOperation(Class<?> loggerClass, ProgressLogger parent);
}
