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
import org.gradle.nativebinaries.platform.Platform;

import java.util.List;

/**
 * Configuration to add support for a target platform to a {@link PlatformConfigurableToolChain}.
 */
@Incubating
public interface TargetPlatformConfiguration {
    /**
     * Matches the platform that this configuration supports.
     */
    boolean supportsPlatform(Platform targetPlatform);

    /**
     * The additional args supplied to the C++ compiler to target the platform.
     */
    List<String> getCppCompilerArgs();

    /**
     * The additional args supplied to the C compiler to target the platform.
     */
    List<String> getCCompilerArgs();

    /**
     * The additional args supplied to the Objective-C++ compiler to target the platform.
     */
    List<String> getObjectiveCppCompilerArgs();

    /**
     * The additional args supplied to the Objective-C compiler to target the platform.
     */
    List<String> getObjectiveCCompilerArgs();

    /**
     * The additional args supplied to the Assembler to target the platform.
     */
    List<String> getAssemblerArgs();

    /**
     * The additional args supplied to the Static Library Archiver to target the platform.
     */
    List<String> getStaticLibraryArchiverArgs();

    /**
     * The additional args supplied to the Linker to target the platform.
     */
    List<String> getLinkerArgs();
}
