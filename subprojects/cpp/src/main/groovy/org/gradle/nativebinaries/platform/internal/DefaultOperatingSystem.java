/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.platform.internal;

import org.gradle.nativebinaries.platform.OperatingSystem;

public class DefaultOperatingSystem implements OperatingSystem {
    private static final org.gradle.internal.os.OperatingSystem CURRENT_OS = org.gradle.internal.os.OperatingSystem.current();
    public static final OperatingSystem TOOL_CHAIN_DEFAULT = new DefaultOperatingSystem("default", CURRENT_OS);

    private final String name;
    private final org.gradle.internal.os.OperatingSystem internalOs;

    public DefaultOperatingSystem(String name, org.gradle.internal.os.OperatingSystem internalOs) {
        this.name = name;
        this.internalOs = internalOs;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return String.format("operating system '%s'", name);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public boolean isCurrent() {
        return internalOs == CURRENT_OS;
    }

    public boolean isWindows() {
        return internalOs.isWindows();
    }

    public boolean isLinux() {
        return internalOs.isLinux();
    }

    public boolean isMacOsX() {
        return internalOs.isMacOsX();
    }

    public boolean isSolaris() {
        return internalOs == org.gradle.internal.os.OperatingSystem.SOLARIS;
    }

    public boolean isFreeBSD() {
        return internalOs == org.gradle.internal.os.OperatingSystem.FREE_BSD;
    }
}
