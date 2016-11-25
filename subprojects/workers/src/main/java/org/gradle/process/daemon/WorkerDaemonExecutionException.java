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

package org.gradle.process.daemon;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.internal.exceptions.Contextual;

/**
 * Indicates that a failure occurred during execution of work in a worker daemon.
 *
 * @since 3.3
 */
@Contextual @Incubating
public class WorkerDaemonExecutionException extends GradleException {
    public WorkerDaemonExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public WorkerDaemonExecutionException(String message) {
        super(message);
    }
}
