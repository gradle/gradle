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

package org.gradle.process.internal.worker;

import org.gradle.api.NonNullApi;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.Logger;
import org.gradle.internal.logging.NoOpLogger;

@NonNullApi
public class WorkerDiagnosticsLogging {
    public static final String WORKER_DIAGNOSTICS_PROPERTY = "org.gradle.process.internal.worker.debug";
    private static final boolean WORKER_DIAGNOSTICS_ENABLED = Boolean.getBoolean(WORKER_DIAGNOSTICS_PROPERTY);
    private static final boolean DEBUG_LOGGING_ENABLED = Logging.getLogger(WorkerDiagnosticsLogging.class).isDebugEnabled();

    public static boolean isWorkerDiagnosticsEnabled() {
        return WORKER_DIAGNOSTICS_ENABLED || DEBUG_LOGGING_ENABLED;
    }

    public static Logger getLogger(Class<?> c) {
        return isWorkerDiagnosticsEnabled() ? Logging.getLogger(c) : new NoOpLogger("worker diagnostics");
    }

    public static Logger getLogger(String name) {
        return isWorkerDiagnosticsEnabled() ? Logging.getLogger(name) : new NoOpLogger("worker diagnostics");
    }
}
