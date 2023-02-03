/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.toolchain;

import org.gradle.api.Incubating;

/**
 * GCC specific settings for the tools used to build for a particular platform.
 */
@Incubating
public interface GccPlatformToolChain extends NativePlatformToolChain {
    /**
     * Returns the settings to use for the C compiler.
     */
    GccCommandLineToolConfiguration getcCompiler();

    /**
     * Returns the settings to use for the C++ compiler.
     */
    GccCommandLineToolConfiguration getCppCompiler();

    /**
     * Returns the settings to use for the Objective-C compiler.
     */
    GccCommandLineToolConfiguration getObjcCompiler();

    /**
     * Returns the settings to use for the Objective-C++ compiler.
     */
    GccCommandLineToolConfiguration getObjcppCompiler();

    /**
     * Returns the settings to use for the assembler.
     */
    GccCommandLineToolConfiguration getAssembler();

    /**
     * Returns the settings to use for the linker.
     */
    GccCommandLineToolConfiguration getLinker();

    /**
     * Returns the settings to use for the archiver.
     */
    GccCommandLineToolConfiguration getStaticLibArchiver();
}
