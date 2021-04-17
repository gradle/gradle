/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.fixtures;

import org.gradle.api.JavaVersion;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.os.OperatingSystem;

import static org.gradle.internal.service.scopes.VirtualFileSystemServices.VFS_DROP_PROPERTY;

public class FileSystemWatchingHelper {

    private static final int WAIT_FOR_CHANGES_PICKED_UP_MILLIS = (OperatingSystem.current().isWindows() || JavaVersion.current().isJava11Compatible())
        ? 120
        : 500;

    public static void waitForChangesToBePickedUp() throws InterruptedException {
        Thread.sleep(WAIT_FOR_CHANGES_PICKED_UP_MILLIS);
    }

    public static String getEnableFsWatchingArgument() {
        return booleanBuildOption(StartParameterBuildOptions.WatchFileSystemOption.LONG_OPTION, true);
    }

    public static String getDisableFsWatchingArgument() {
        return booleanBuildOption(StartParameterBuildOptions.WatchFileSystemOption.LONG_OPTION, false);
    }

    public static String getDropVfsArgument() {
        return getDropVfsArgument(true);
    }

    public static String getDropVfsArgument(boolean drop) {
        return systemProperty(VFS_DROP_PROPERTY, drop);
    }

    public static String getVerboseVfsLoggingArgument() {
        return systemProperty(StartParameterBuildOptions.VfsVerboseLoggingOption.GRADLE_PROPERTY, true);
    }

    private static String systemProperty(String key, Object value) {
        return "-D" + key + "=" + value;
    }

    private static String booleanBuildOption(String optionName, boolean enabled) {
        return "--" + (enabled ? "" : "no-") + optionName;
    }
}
