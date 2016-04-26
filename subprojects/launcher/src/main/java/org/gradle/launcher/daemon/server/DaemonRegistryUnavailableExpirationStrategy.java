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
package org.gradle.launcher.daemon.server;

import org.gradle.launcher.daemon.registry.DaemonDir;

import java.io.File;

public class DaemonRegistryUnavailableExpirationStrategy implements DaemonExpirationStrategy {
    @Override
    public boolean shouldExpire(Daemon daemon) {
        try {
            File daemonRegistryDir = daemon.getDaemonContext().getDaemonRegistryDir();
            if (!new DaemonDir(daemonRegistryDir).getRegistry().canRead()) {
                return true;
            }
        } catch (SecurityException se) {
            // TODO(ew): Hook up logging
            return true;
        }
        return false;
    }
}

