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

package org.gradle.process.internal.worker;

import org.gradle.internal.remote.ObjectConnection;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.health.memory.JvmMemoryStatus;

/**
 * A child JVM that performs some worker action. You can send and receive messages to/from the worker action
 * using a supplied {@link ObjectConnection}.
 */
public interface WorkerProcess {
    WorkerProcess start();

    /**
     * The connection to the worker. Call {@link ObjectConnection#connect()} to complete the connection.
     */
    ObjectConnection getConnection();

    ExecResult waitForStop();

    JvmMemoryStatus getJvmMemoryStatus();
}
