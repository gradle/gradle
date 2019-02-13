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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult;
import org.gradle.platform.base.internal.toolchain.ToolProvider;

public interface PlatformToolProvider extends ToolProvider {
    String getObjectFileExtension();

    String getExecutableName(String executablePath);

    String getSharedLibraryName(String libraryPath);

    /**
     * Does this tool chain produce an import library when linking a shared library?
     */
    boolean producesImportLibrary();

    /**
     * Whether or not this tool chain requires a debuggable binary to be stripped or whether the binary is stripped by default.
     */
    boolean requiresDebugBinaryStripping();

    String getImportLibraryName(String libraryPath);

    String getSharedLibraryLinkFileName(String libraryPath);

    String getStaticLibraryName(String libraryPath);

    String getExecutableSymbolFileName(String executablePath);

    String getLibrarySymbolFileName(String libraryPath);

    CompilerMetadata getCompilerMetadata(ToolType compilerType);

    SystemLibraries getSystemLibraries(ToolType compilerType);

    CommandLineToolSearchResult locateTool(ToolType compilerType);

    boolean isSupported();
}
