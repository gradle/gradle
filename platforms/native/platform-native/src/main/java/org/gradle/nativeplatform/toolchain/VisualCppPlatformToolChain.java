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
 * Visual C++ specific settings for the tools used to build for a particular platform.
 * @since 2.2
 */
@Incubating
public interface VisualCppPlatformToolChain extends NativePlatformToolChain {
    /**
     * Returns the settings to use for the C compiler.
     * @since 2.2
     */
    CommandLineToolConfiguration getcCompiler();

    /**
     * Returns the settings to use for the C++ compiler.
     * @since 2.2
     */
    CommandLineToolConfiguration getCppCompiler();

    /**
     * Returns the settings to use for the Windows resources compiler.
     * @since 2.2
     */
    CommandLineToolConfiguration getRcCompiler();

    /**
     * Returns the settings to use for the assembler.
     * @since 2.2
     */
    CommandLineToolConfiguration getAssembler();

    /**
     * Returns the settings to use for the linker.
     * @since 2.2
     */
    CommandLineToolConfiguration getLinker();

    /**
     * Returns the settings to use for the archiver.
     * @since 2.2
     */
    CommandLineToolConfiguration getStaticLibArchiver();
}
