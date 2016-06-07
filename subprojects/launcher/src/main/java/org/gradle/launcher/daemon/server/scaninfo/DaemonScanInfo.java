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

package org.gradle.launcher.daemon.server.scaninfo;

import org.gradle.api.Action;

/**
 * A means to expose Daemon information _specifically_ for the purpose of build scans. The associated plugin obtains this via the service registry and queries all values when it is applied.
 *
 * This API is a contract with the plugin. Any binary incompatible changes will require changes to the plugin.
 */
public interface DaemonScanInfo {
    /**
     * @return the number of builds that the daemon has run
     */
    int getNumberOfBuilds();

    /**
     * @return The time (milliseconds) since epoch at which the daemon was started
     */
    long getStartedAt();

    /**
     * @return The idle timeout (milliseconds) of the daemon
     */
    long getIdleTimeout();

    /**
     * @return The number of running daemons
     */
    int getNumberOfRunningDaemons();

    /**
     * Invokes the given action when the Daemon becomes unhealthy in way that requires it be terminated at the end of the build.
     * <p>
     * The action will be invoked at-most once during a build.
     * It will only be invoked for the build in which it was registered (i.e. not subsequent builds).
     * Each action provided to each invocation of this message will be notified.
     * <p>
     * The action receives a free-form, human friendly, string describing why the Daemon needs to be shut down.
     */
    void notifyOnUnhealthy(Action<? super String> listener);
}
