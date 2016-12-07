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
package org.gradle.process.internal.daemon;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class WorkerDaemonServer implements WorkerDaemonProtocol {
    private static final Logger LOGGER = Logging.getLogger(WorkerDaemonServer.class);

    @Override
    public <T extends WorkSpec> WorkerDaemonResult execute(WorkerDaemonAction<T> action, T spec) {
        try {
            LOGGER.info("Executing {} in worker daemon.", action.getDescription());
            WorkerDaemonResult result = action.execute(spec);
            LOGGER.info("Successfully executed {} in worker daemon.", action.getDescription());
            return result;
        } catch (Throwable t) {
            LOGGER.info("Exception executing {} in worker daemon: {}.", action.getDescription(), t);
            return new WorkerDaemonResult(true, t);
        }
    }

    @Override
    public String toString() {
        return "WorkerDaemonServer{}";
    }
}
