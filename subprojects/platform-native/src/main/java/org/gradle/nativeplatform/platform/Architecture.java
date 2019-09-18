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
import org.gradle.internal.HasInternalProtocol;

/**
 * A cpu architecture.
 *
 * <table>
 *     <tr>
 *         <th>Instruction Set</th>
 *         <th>32-bit names</th>
 *         <th>64-bit names</th>
 *     </tr>
 *     <tr>
 *         <td>Intel x86</td>
 *         <td>"x86", "i386", "ia-32", "i686"</td>
 *         <td>"x86_64", "amd64", "x64", "x86-64"</td>
 *     </tr>
 *     <tr>
 *         <td>Intel Itanium</td>
 *         <td>N/A</td>
 *         <td>"ia-64", "ia64"</td>
 *     </tr>
 *     <tr>
 *         <td>Power PC</td>
 *         <td>"ppc"</td>
 *         <td>"ppc64"</td>
 *     </tr>
 *     <tr>
 *         <td>Sparc</td>
 *         <td>"sparc", "sparc32", "sparc-v8"</td>
 *         <td>"sparc64", "ultrasparc", "sparc-v9"</td>
 *     </tr>
 *     <tr>
 *         <td>ARM</td>
 *         <td>"arm", "arm-v7", "armv7", "arm32"</td>
 *         <td>"arm64", "arm-v8"</td>
 *     </tr>
 * </table>
 */
@HasInternalProtocol
public interface Architecture extends Named {
    @Override
    @Input
    String getName();

    /**
     * Returns a human-consumable display name for this architecture.
     */
    @Internal
    String getDisplayName();
}
