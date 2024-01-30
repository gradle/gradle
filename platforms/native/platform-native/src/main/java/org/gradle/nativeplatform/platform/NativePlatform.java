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

import org.gradle.api.Describable;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.platform.base.Platform;

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
@HasInternalProtocol
public interface NativePlatform extends Platform, Describable {
    /**
     * The cpu architecture being targeted. Defaults to the default architecture produced by the tool chain.
     */
    @Nested
    Architecture getArchitecture();

    /**
     * Sets the cpu architecture being targeted.
     * <p>
     * The architecture is provided as a string name, which is translated into one of the supported architecture types.
     * </p>
     * @see Architecture Supported notations.
     */
    void architecture(String name);

    /**
     * The operating system being targeted.
     * Defaults to the default operating system targeted by the tool chain (normally the current operating system).
     */
    @Nested
    OperatingSystem getOperatingSystem();

    /**
     * Sets the operating system being targeted.
     * <p>
     * The operating system is provided as a string name, which is translated into one of the supported operating system types.
     * </p>
     * @see OperatingSystem Supported notations.
     */
    void operatingSystem(String name);
}
