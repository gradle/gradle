/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.tasks.Input;

/**
 * Represents the operating system of a configuration. Typical operating system include Windows, Linux, and macOS.
 * This interface allows the user to customize operating systems by implementing this interface.
 *
 * @since 5.1
 */
public abstract class OperatingSystemFamily implements Named {
    public static final Attribute<OperatingSystemFamily> OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("org.gradle.native.operatingSystem", OperatingSystemFamily.class);

    /**
     * {@inheritDoc}
     */
    @Input
    @Override
    public abstract String getName();

    /**
     * The Windows operating system family.
     *
     */
    public static final String WINDOWS = "windows";

    /**
     * Is this the Windows operating system family?
     *
     */
    public boolean isWindows() {
        return is(WINDOWS);
    }

    /**
     * The Linux operating system family.
     *
     */
    public static final String LINUX = "linux";

    /**
     * Is this the Linux operating system family?
     *
     */
    public boolean isLinux() {
        return is(LINUX);
    }

    /**
     * The macOS operating system family.
     *
     */
    public static final String MACOS = "macos";

    /**
     * Is this the macOS operating system family?
     *
     */
    public boolean isMacOs() {
        return is(MACOS);
    }

    private boolean is(String osFamily) {
        return getName().equals(osFamily);
    }
}
