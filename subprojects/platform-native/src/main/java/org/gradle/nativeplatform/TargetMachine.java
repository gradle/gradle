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

import org.gradle.api.Incubating;
import org.gradle.api.tasks.Nested;

/**
 * Represents a combination of operating system and cpu architecture that a variant might be built for.
 *
 * @since 5.1
 */
@Incubating
public interface TargetMachine {
    /**
     * Returns the target operating system
     */
    @Nested
    OperatingSystemFamily getOperatingSystemFamily();

    /**
     * Returns the target architecture
     */
    @Nested
    MachineArchitecture getArchitecture();

    /**
     * Returns a {@link TargetMachine} for the x86 32-bit architecture
     */
    TargetMachine x86();

    /**
     * Returns a {@link TargetMachine} for the x86 64-bit architecture
     */
    TargetMachine x64();

    /**
     * Returns a {@link TargetMachine} for the itanium architecture
     */
    TargetMachine ia64();

    /**
     * Returns a {@link TargetMachine} for the arm 32-bit architecture
     */
    TargetMachine arm();

    /**
     * Returns a {@link TargetMachine} for the arm 64-bit architecture
     */
    TargetMachine arm64();

    /**
     * Returns a {@link TargetMachine} for the PowerPC 32-bit architecture
     */
    TargetMachine ppc();

    /**
     * Returns a {@link TargetMachine} for the PowerPC 64-bit architecture
     */
    TargetMachine ppc64();

    /**
     * Returns a {@link TargetMachine} for the Sparc 32-bit architecture
     */
    TargetMachine sparc();

    /**
     * Returns a {@link TargetMachine} for the Sparc 64-bit architecture
     */
    TargetMachine sparc64();
}
