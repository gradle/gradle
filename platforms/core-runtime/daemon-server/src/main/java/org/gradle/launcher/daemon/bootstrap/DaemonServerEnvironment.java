/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.bootstrap;

import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;

/**
 * The environment-specific steps of starting a daemon server, distinguishing a daemon
 * running in its own forked process from a daemon embedded in a client process.
 */
public interface DaemonServerEnvironment {

    /**
     * Prepares the environment to run the daemon server. Invoked after the daemon services are
     * created and before the daemon logging manager is started.
     */
    void beforeStart(LoggingManagerInternal loggingManager, File daemonLog, ServiceRegistry daemonServices);

}
