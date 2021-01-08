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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.internal.logging.text.DiagnosticsVisitor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.internal.StripperSpec;
import org.gradle.nativeplatform.internal.SymbolExtractorSpec;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.WindowsResourceCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata;

import static org.gradle.internal.FileUtils.withExtension;

public abstract class AbstractPlatformToolProvider implements PlatformToolProvider {
    protected final OperatingSystemInternal targetOperatingSystem;
    protected final BuildOperationExecutor buildOperationExecutor;

    public AbstractPlatformToolProvider(BuildOperationExecutor buildOperationExecutor, OperatingSystemInternal targetOperatingSystem) {
        this.targetOperatingSystem = targetOperatingSystem;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void explain(DiagnosticsVisitor visitor) {
    }

    @Override
    public String getExecutableName(String executablePath) {
        return targetOperatingSystem.getInternalOs().getExecutableName(executablePath);
    }

    @Override
    public String getSharedLibraryName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getSharedLibraryName(libraryPath);
    }

    @Override
    public boolean producesImportLibrary() {
        return false;
    }

    @Override
    public boolean requiresDebugBinaryStripping() {
        return true;
    }

    @Override
    public String getImportLibraryName(String libraryPath) {
        return getSharedLibraryLinkFileName(libraryPath);
    }

    @Override
    public String getSharedLibraryLinkFileName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getSharedLibraryName(libraryPath);
    }

    @Override
    public String getStaticLibraryName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getStaticLibraryName(libraryPath);
    }

    @Override
    public String getExecutableSymbolFileName(String libraryPath) {
        return withExtension(getExecutableName(libraryPath), SymbolExtractorOsConfig.current().getExtension());
    }

    @Override
    public String getLibrarySymbolFileName(String libraryPath) {
        return withExtension(getSharedLibraryName(libraryPath), SymbolExtractorOsConfig.current().getExtension());
    }

    @Override
    public <T> T get(Class<T> toolType) {
        throw new IllegalArgumentException(String.format("Don't know how to provide tool of type %s.", toolType.getSimpleName()));
    }

    @Override
    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(Class<T> spec) {
        if (CppCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createCppCompiler());
        }
        if (CppPCHCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createCppPCHCompiler());
        }
        if (CCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createCCompiler());
        }
        if (CPCHCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createCPCHCompiler());
        }
        if (ObjectiveCppCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createObjectiveCppCompiler());
        }
        if (ObjectiveCppPCHCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createObjectiveCppPCHCompiler());
        }
        if (ObjectiveCCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createObjectiveCCompiler());
        }
        if (ObjectiveCPCHCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createObjectiveCPCHCompiler());
        }
        if (WindowsResourceCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createWindowsResourceCompiler());
        }
        if (AssembleSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createAssembler());
        }
        if (LinkerSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createLinker());
        }
        if (StaticLibraryArchiverSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createStaticLibraryArchiver());
        }
        if (SymbolExtractorSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createSymbolExtractor());
        }
        if (StripperSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createStripper());
        }
        throw new IllegalArgumentException(String.format("Don't know how to compile from a spec of type %s.", spec.getClass().getSimpleName()));
    }

    protected final RuntimeException unavailableTool(String message) {
        return new RuntimeException(message);
    }

    protected Compiler<?> createCppCompiler() {
        throw unavailableTool("C++ compiler is not available");
    }

    protected Compiler<?> createCppPCHCompiler() {
        throw unavailableTool("C++ pre-compiled header compiler is not available");
    }

    protected Compiler<?> createCCompiler() {
        throw unavailableTool("C compiler is not available");
    }

    protected Compiler<?> createCPCHCompiler() {
        throw unavailableTool("C pre-compiled header compiler is not available");
    }

    protected Compiler<?> createObjectiveCppCompiler() {
        throw unavailableTool("Objective-C++ compiler is not available");
    }

    protected Compiler<?> createObjectiveCppPCHCompiler() {
        throw unavailableTool("Objective-C++ pre-compiled header compiler is not available");
    }

    protected Compiler<?> createObjectiveCCompiler() {
        throw unavailableTool("Objective-C compiler is not available");
    }

    protected Compiler<?> createObjectiveCPCHCompiler() {
        throw unavailableTool("Objective-C compiler is not available");
    }

    protected Compiler<?> createWindowsResourceCompiler() {
        throw unavailableTool("Windows resource compiler is not available");
    }

    protected Compiler<?> createAssembler() {
        throw unavailableTool("Assembler is not available");
    }

    protected Compiler<?> createLinker() {
        throw unavailableTool("Linker is not available");
    }

    protected Compiler<?> createStaticLibraryArchiver() {
        throw unavailableTool("Static library archiver is not available");
    }

    protected Compiler<?> createSymbolExtractor() {
        throw unavailableTool("Symbol extractor is not available");
    }

    protected Compiler<?> createStripper() {
        throw unavailableTool("Stripper is not available");
    }

    @Override
    public String getObjectFileExtension() {
        return targetOperatingSystem.isWindows() ? ".obj" : ".o";
    }

    @Override
    public CompilerMetadata getCompilerMetadata(ToolType compilerType) {
        throw unavailableTool("Compiler is not available");
    }
}
