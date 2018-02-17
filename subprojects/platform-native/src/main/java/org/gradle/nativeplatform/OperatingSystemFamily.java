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
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * Represents the operating system of a configuration. Typical operating system include Windows, Linux, and macOS.
 * This interface allows the user to customize operating systems by implementing this interface.
 *
 * @since 4.7
 */
@Incubating
public interface OperatingSystemFamily extends Named {
    Attribute<OperatingSystemFamily> OPERATING_SYSTEM_ATTRIBUTE = Attribute.of("org.gradle.native.operatingSystem", OperatingSystemFamily.class);

    /**
     * The Windows operating system family.
     *
     * @since 4.7
     */
    String WINDOWS = "windows";

    /**
     * The Linux operating system family.
     *
     * @since 4.7
     */
    String LINUX = "linux";

    /**
     * The macOS operating system family.
     *
     * @since 4.7
     */
    String MAC_OS = "macos";
}
