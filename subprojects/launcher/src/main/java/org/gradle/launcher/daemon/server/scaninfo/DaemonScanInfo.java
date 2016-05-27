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

/**
 * A means to expose daemon specific information to the Build Receipt plugin.
 * Effectively, forms a not-quite-public contract with the Build Receipt plugin.
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
}
