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

import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.internal.StaticLibraryArchiverSpec;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.language.c.internal.CCompileSpec;
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec;
import org.gradle.nativebinaries.language.objectivec.internal.ObjectiveCCompileSpec;
import org.gradle.nativebinaries.language.objectivecpp.internal.ObjectiveCppCompileSpec;
import org.gradle.nativebinaries.language.rc.internal.WindowsResourceCompileSpec;

public interface PlatformToolChain extends ToolSearchResult {
    Compiler<CppCompileSpec> createCppCompiler();

    Compiler<CCompileSpec> createCCompiler();

    Compiler<ObjectiveCppCompileSpec> createObjectiveCppCompiler();

    Compiler<ObjectiveCCompileSpec> createObjectiveCCompiler();

    Compiler<AssembleSpec> createAssembler();

    Compiler<WindowsResourceCompileSpec> createWindowsResourceCompiler();

    Compiler<LinkerSpec> createLinker();

    Compiler<StaticLibraryArchiverSpec> createStaticLibraryArchiver();
}
