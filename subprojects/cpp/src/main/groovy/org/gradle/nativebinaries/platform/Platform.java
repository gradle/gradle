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
import org.gradle.internal.HasInternalProtocol;

/**
 * A target platform for building native binaries. Each target platform is given a name, and may optionally be given
 * a specific {@link Architecture} and/or {@link OperatingSystem} to target.
 *
 * <pre>
 *     model {
 *         platforms {
 *             windows_x86 {
 *                 architecture "i386"
 *                 operatingSystem "windows"
 *             }
 *         }
 *     }
 * </pre>
 */
@Incubating
@HasInternalProtocol
public interface Platform extends Named {
    /**
     * Returns a human-consumable display name for this platform.
     */
    String getDisplayName();

    /**
     * The cpu architecture being targeted. Defaults to the default architecture produced by the tool chain.
     */
    Architecture getArchitecture();

    /**
     * Sets the cpu architecture being targeted.
     * The architecture is provided as a string name, which is translated into one of the supported architecture types.
     *
     * <table>
     *     <tr>
     *         <th>Instruction Set</th>
     *         <th>32-bit names</th>
     *         <th>64-bit names</th>
     *     </tr>
     *     <tr>
     *         <td>Intel x86</td>
     *         <td>"x86", "i386", "ia-32"</td>
     *         <td>"x86_64", "amd64", "x64", "x86-64"</td>
     *     </tr>
     *     <tr>
     *         <td>Intel Itanium</td>
     *         <td></td>
     *         <td>"ia-64"</td>
     *     </tr>
     *     <tr>
     *         <td>Power PC</td>
     *         <td>"ppc"</td>
     *         <td>"ppc64"</td>
     *     </tr>
     *     <tr>
     *         <td>Sparc</td>
     *         <td>"sparc", "sparc32", "sparc-v7", "sparc-v8"</td>
     *         <td>"sparc64", "ultrasparc", "sparc-v9"</td>
     *     </tr>
     *     <tr>
     *         <td>ARM</td>
     *         <td>"arm"</td>
     *         <td></td>
     *     </tr>
     * </table>
     */
    void architecture(Object notation);

    /**
     * The operating system being targeted.
     * Defaults to the default operating system targeted by the tool chain (normally the current operating system).
     */
    OperatingSystem getOperatingSystem();

    /**
     * Sets the operating system being targeted.
     * The operating system is provided as a string name, which is translated into one of the supported operating system types.
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
    void operatingSystem(Object notation);

}
