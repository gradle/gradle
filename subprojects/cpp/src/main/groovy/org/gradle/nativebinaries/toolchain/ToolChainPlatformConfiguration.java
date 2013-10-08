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
package org.gradle.nativebinaries.toolchain;

import org.gradle.api.Incubating;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.Platform;

/**
 * Configuration to add support for a target platform to a {@link PlatformConfigurableToolChain}.
 */
@Incubating
public interface ToolChainPlatformConfiguration {
    /**
     * Matches the platform that this configuration supports.
     */
    boolean supportsPlatform(Platform element);

    /**
     * Configure the binary to build for this platform.
     */
    void configureBinaryForPlatform(NativeBinary binary);
}
