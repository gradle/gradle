/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.platform;

import org.gradle.api.Incubating;
import org.gradle.platform.internal.DefaultBuildPlatform;

/**
 * Factory for creating {@link BuildPlatform} instances.
 *
 * @since 8.13
 */
@Incubating
public class BuildPlatformFactory {

    /**
     * Creates a new {@link BuildPlatform} instance with the given architecture and operating system.
     *
     * @param architecture the architecture of the platform
     * @param os the operating system of the platform
     * @return the new {@link BuildPlatform} instance
     *
     * @since 8.13
     */
    public static BuildPlatform of(Architecture architecture, OperatingSystem os) {
        return new DefaultBuildPlatform(architecture, os);
    }

    private BuildPlatformFactory() {
        // Factory class, do not instantiate
    }
}
