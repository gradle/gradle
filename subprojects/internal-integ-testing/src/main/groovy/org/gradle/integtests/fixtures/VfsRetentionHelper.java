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

import static org.gradle.internal.service.scopes.VirtualFileSystemServices.VFS_DROP_PROPERTY;
import static org.gradle.internal.service.scopes.VirtualFileSystemServices.VFS_RETENTION_ENABLED_PROPERTY;

public class VfsRetentionHelper {

    private static final int WAIT_FOR_CHANGES_PICKED_UP_MILLIS = 80;

    public static void waitForChangesToBePickedUp() throws InterruptedException {
        Thread.sleep(WAIT_FOR_CHANGES_PICKED_UP_MILLIS);
    }

    public static String getEnableVfsRetentionArgument() {
        return systemProperty(VFS_RETENTION_ENABLED_PROPERTY, true);
    }

    public static String getDisableVfsRetentionArgument() {
        return systemProperty(VFS_RETENTION_ENABLED_PROPERTY, false);
    }

    public static String getDropVfsArgument() {
        return systemProperty(VFS_DROP_PROPERTY, true);
    }

    private static String systemProperty(String key, Object value) {
        return "-D" + key + "=" + value;
    }
}
