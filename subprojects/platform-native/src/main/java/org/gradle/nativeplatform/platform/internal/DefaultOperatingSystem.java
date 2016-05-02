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
package org.gradle.nativeplatform.platform.internal;

import org.gradle.internal.os.OperatingSystem;

public class DefaultOperatingSystem implements OperatingSystemInternal {
    private static final OperatingSystem CURRENT_OS = OperatingSystem.current();

    private final String name;
    private final OperatingSystem internalOs;

    public DefaultOperatingSystem(String name) {
        this(name, OperatingSystem.forName(name));
    }

    public DefaultOperatingSystem(String name, OperatingSystem internalOs) {
        this.name = name;
        this.internalOs = internalOs;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return "operating system '" + name + "'";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public OperatingSystem getInternalOs() {
        return internalOs;
    }

    @Override
    public boolean isCurrent() {
        return internalOs == CURRENT_OS;
    }

    @Override
    public boolean isWindows() {
        return internalOs.isWindows();
    }

    @Override
    public boolean isLinux() {
        return internalOs.isLinux();
    }

    @Override
    public boolean isMacOsX() {
        return internalOs.isMacOsX();
    }

    @Override
    public boolean isSolaris() {
        return internalOs == OperatingSystem.SOLARIS;
    }

    @Override
    public boolean isFreeBSD() {
        return internalOs == OperatingSystem.FREE_BSD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultOperatingSystem that = (DefaultOperatingSystem) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
