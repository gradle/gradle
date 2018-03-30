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

package org.gradle.language;

import org.gradle.api.Incubating;
import org.gradle.api.provider.SetProperty;
import org.gradle.nativeplatform.OperatingSystemFamily;

/**
 * Represents a component with output files.
 *
 * @since 4.7
 */
@Incubating
public interface ComponentWithTargets {
    /**
     * Specifies the set of operating systems for the target machine.
     *
     * @since 4.7
     */
    SetProperty<OperatingSystemFamily> getOperatingSystems();

    /**
     * Adds a operating system to the set for the target machine.
     *
     * @since 4.7
     */
    void operatingSystem(OperatingSystemFamily operatingSystemFamily);

    /**
     * Builder for defining a target machine
     *
     * @since 4.7
     */
    TargetMachine getTargets();

    /**
     * Target machine builder
     *
     * @since 4.7
     *
     * TODO: Expand this for architecture and other target attributes.
     */
    @Incubating
    interface TargetMachine {
        /**
         * Target machine for Windows
         */
        OperatingSystemFamily windows();
        /**
         * Target machine for Linux
         */
        OperatingSystemFamily linux();
        /**
         * Target machine for macOS
         */
        OperatingSystemFamily macOS();
    }
}
