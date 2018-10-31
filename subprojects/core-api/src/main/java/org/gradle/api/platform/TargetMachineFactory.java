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

package org.gradle.api.platform;

import org.gradle.api.Incubating;
import org.gradle.api.platform.TargetMachine;

/**
 * A factory for creating {@link TargetMachine} objects.
 *
 * @since 5.1
 */
@Incubating
public interface TargetMachineFactory {
    /**
     * Returns a {@link TargetMachine} representing the operating system and architecture of the current host.
     */
    TargetMachine host();

    /**
     * Returns a {@link TargetMachine} for the windows operating system family and the architecture of the current host.
     */
    TargetMachine windows();

    /**
     * Returns a {@link TargetMachine} for the linux operating system family and the architecture of the current host.
     */
    TargetMachine linux();

    /**
     * Returns a {@link TargetMachine} for the macOS operating system family and the architecture of the current host.
     */
    TargetMachine macOS();

    /**
     * Returns a {@link TargetMachine} representing the specified operating system and the architecture of the current host.
     */
    TargetMachine of(String operatingSystemFamily);

    /**
     * Returns a {@link TargetMachine} representing the operating system and architecture specified.
     */
    TargetMachine of(String operatingSystemFamily, String architecture);
}
