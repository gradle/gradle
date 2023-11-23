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

import org.gradle.api.GradleException;
import org.gradle.internal.logging.text.DiagnosticsVisitor;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult;
import org.gradle.platform.base.internal.toolchain.ToolSearchResult;

import java.io.File;

import static org.gradle.internal.FileUtils.withExtension;

public class UnavailablePlatformToolProvider implements PlatformToolProvider, CommandLineToolSearchResult {
    private final ToolSearchResult failure;
    private final OperatingSystemInternal targetOperatingSystem;

    public UnavailablePlatformToolProvider(OperatingSystemInternal targetOperatingSystem, ToolSearchResult failure) {
        this.targetOperatingSystem = targetOperatingSystem;
        this.failure = failure;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void explain(DiagnosticsVisitor visitor) {
        failure.explain(visitor);
    }

    private RuntimeException failure() {
        TreeFormatter formatter = new TreeFormatter();
        this.explain(formatter);
        return new GradleException(formatter.toString());
    }

    @Override
    public boolean requiresDebugBinaryStripping() {
        // Doesn't really make sense
        return true;
    }

    @Override
    public String getObjectFileExtension() {
        throw failure();
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
        // Doesn't really make sense
        return targetOperatingSystem.isWindows();
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
    public String getLibrarySymbolFileName(String libraryPath) {
        return withExtension(getSharedLibraryName(libraryPath), SymbolExtractorOsConfig.current().getExtension());
    }

    @Override
    public String getExecutableSymbolFileName(String executablePath) {
        return withExtension(getExecutableName(executablePath), SymbolExtractorOsConfig.current().getExtension());
    }

    @Override
    public <T> T get(Class<T> toolType) {
        throw new IllegalArgumentException(String.format("Don't know how to provide tool of type %s.", toolType.getSimpleName()));
    }

    @Override
    public <T extends CompileSpec> Compiler<T> newCompiler(Class<T> specType) {
        throw failure();
    }

    @Override
    public CommandLineToolSearchResult locateTool(ToolType compilerType) {
        return this;
    }

    @Override
    public File getTool() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SystemLibraries getSystemLibraries(ToolType compilerType) {
        return new EmptySystemLibraries();
    }

    @Override
    public CompilerMetadata getCompilerMetadata(ToolType compilerType) {
        throw failure();
    }
}
