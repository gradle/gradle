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
package org.gradle.nativeplatform.platform;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/**
 * A machine operating system.
 *
 * <table>
 *     <tr>
 *         <th>Operating System</th>
 *         <th>Aliases</th>
 *     </tr>
 *     <tr>
 *         <td>Windows</td>
 *         <td>"windows"</td>
 *     </tr>
 *     <tr>
 *         <td>GNU/Linux</td>
 *         <td>"linux"</td>
 *     </tr>
 *     <tr>
 *         <td>Mac OS X</td>
 *         <td>"osx", "mac os x", "darwin"</td>
 *     </tr>
 *     <tr>
 *         <td>Solaris</td>
 *         <td>"solaris", "sunos"</td>
 *     </tr>
 * </table>
 */
@Incubating
public interface OperatingSystem extends Named {
    @Input
    @Override
    String getName();

    /**
     * Returns a human-consumable display name for this operating system.
     */
    @Internal
    String getDisplayName();

    /**
     * Is this the current OS?
     */
    @Internal
    boolean isCurrent();

    /**
     * Is it Windows?
     */
    @Internal
    boolean isWindows();

    /**
     * Is it Mac OS X?
     */
    @Internal
    boolean isMacOsX();

    /**
     * Is it Linux?
     */
    @Internal
    boolean isLinux();

    /**
     * Is it Solaris?
     */
    @Internal
    boolean isSolaris();

    /**
     * Is it FreeBSD?
     */
    @Internal
    boolean isFreeBSD();
}
