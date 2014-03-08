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

package org.gradle.nativebinaries.toolchain.internal;

import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.ToolChain;

public interface ToolChainInternal extends ToolChain {
    /**
     * Locates the tools that can target the given platform.
     */
    PlatformToolChain target(Platform targetPlatform);

    // TODO:DAZ These are platform-specific
    String getExecutableName(String executablePath);

    String getSharedLibraryName(String libraryPath);

    String getSharedLibraryLinkFileName(String libraryPath);

    String getStaticLibraryName(String libraryPath);

    /**
     * Returns a unique identifier for the output produced by this toolchain on the current platform.
     */
    String getOutputType();
}
