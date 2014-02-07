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
package org.gradle.nativebinaries.platform;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

/**
 * A machine operating system.
 */
@Incubating
public interface OperatingSystem extends Named {
    /**
     * Returns a human-consumable display name for this operating system.
     */
    String getDisplayName();

    /**
     * Is this the current OS?
     */
    boolean isCurrent();

    /**
     * Is it Windows?
     */
    boolean isWindows();

    /**
     * Is it Mac OS X?
     */
    boolean isMacOsX();

    /**
     * Is it Linux?
     */
    boolean isLinux();

    /**
     * Is it Solaris?
     */
    boolean isSolaris();

    /**
     * Is it FreeBSD?
     */
    boolean isFreeBSD();
}
