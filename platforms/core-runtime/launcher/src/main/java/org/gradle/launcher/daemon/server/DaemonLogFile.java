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

package org.gradle.launcher.daemon.server;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.UUID;

/**
 * Carries the location of the log file for the current daemon.
 */
@ServiceScope(Scope.Global.class)
public class DaemonLogFile {
    public static final String DAEMON_LOG_SUFFIX = ".out.log";
    public static final String DAEMON_LOG_PREFIX = "daemon-";
    private final File file;

    public DaemonLogFile(File file) {
        this.file = file;
    }

    static String getDaemonLogFileName(@Nullable Long pid) {
        return DAEMON_LOG_PREFIX + (pid == null ? UUID.randomUUID() : pid) + DAEMON_LOG_SUFFIX;
    }

    public File getFile() {
        return file;
    }
}
