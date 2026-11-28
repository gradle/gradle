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

import org.gradle.api.Named;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/**
 * A machine operating system.
 *
 * <table>
 * <caption>Values</caption>
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
 *         <td>MacOS</td>
 *         <td>"osx", "mac os x", "darwin"</td>
 *     </tr>
 *     <tr>
 *         <td>Solaris</td>
 *         <td>"solaris", "sunos"</td>
 *     </tr>
 *     <tr>
 *         <td>FreeBSD</td>
 *         <td>"freebsd"</td>
 *     </tr>
 * </table>
 * @since 2.2
 */
public interface OperatingSystem extends Named {
    @Input
    @Override
    String getName();

    /**
     * Returns a human-consumable display name for this operating system.
     * @since 2.2
     */
    @Internal
    String getDisplayName();

    /**
     * Is this the current OS?
     * @since 2.2
     */
    @Internal
    boolean isCurrent();

    /**
     * Is it Windows?
     * @since 2.2
     */
    @Internal
    boolean isWindows();

    /**
     * Is it macOS?
     * @since 2.2
     */
    @Internal
    boolean isMacOsX();

    /**
     * Is it Linux?
     * @since 2.2
     */
    @Internal
    boolean isLinux();

    /**
     * Is it Solaris?
     * @since 2.2
     */
    @Internal
    boolean isSolaris();

    /**
     * Is it FreeBSD?
     * @since 2.2
     */
    @Internal
    boolean isFreeBSD();
}
