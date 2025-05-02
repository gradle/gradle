/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.fixtures.daemon;

import java.io.File;
import java.util.List;

public interface DaemonsFixture {
    /**
     * Kills all daemons.
     */
    void killAll();

    /**
     * Returns all known daemons. Includes any daemons that are no longer running.
     */
    List<? extends DaemonFixture> getDaemons();

    /**
     * Returns all daemons that are visible to clients. May include daemons that are no longer running (eg they have crashed).
     */
    List<? extends DaemonFixture> getVisible();

    /**
     * Convenience to get a single daemon. Fails if there is not exactly 1 daemon.
     */
    DaemonFixture getDaemon();

    /**
     * Returns the base dir of the daemon.
     */
    File getDaemonBaseDir();

    /**
     * Returns the Gradle version of the daemon.
     */
    String getVersion();
}
