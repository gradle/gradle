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

import com.google.common.util.concurrent.MoreExecutors;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.compilespec.*;
import org.gradle.util.TreeVisitor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 */
public class AbstractPlatformToolProvider implements PlatformToolProvider {
    protected final OperatingSystemInternal targetOperatingSystem;
    protected final ExecutorFactory executorFactory;

    public AbstractPlatformToolProvider(OperatingSystemInternal targetOperatingSystem, final int numberOfThreads) {
        this.targetOperatingSystem = targetOperatingSystem;
        this.executorFactory = createExecutorFactory(numberOfThreads);
    }

    protected static ExecutorFactory createExecutorFactory(final int numberOfThreads) {
        return new DefaultExecutorFactory() {
            @Override
            protected ExecutorService createExecutor(String displayName) {
                if (numberOfThreads < 0) {
                    return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactoryImpl(displayName));
                } else if (numberOfThreads == 0) {
                    return MoreExecutors.sameThreadExecutor();
                } else {
                    return Executors.newFixedThreadPool(numberOfThreads, new ThreadFactoryImpl(displayName));
                }
            }
        };
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {
    }

    public String getObjectFileExtension() {
        return targetOperatingSystem.isWindows() ? "obj" : "o";
    }

    public String getExecutableName(String executablePath) {
        return targetOperatingSystem.getInternalOs().getExecutableName(executablePath);
    }

    public String getSharedLibraryName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getSharedLibraryName(libraryPath);
    }

    public String getSharedLibraryLinkFileName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getSharedLibraryName(libraryPath);
    }

    public String getStaticLibraryName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getStaticLibraryName(libraryPath);
    }

    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(T spec) {
        if (spec instanceof CppCompileSpec) {
            return castCompiler(createCppCompiler());
        }
        if (spec instanceof CCompileSpec) {
            return castCompiler(createCCompiler());
        }
        if (spec instanceof ObjectiveCppCompileSpec) {
            return castCompiler(createObjectiveCppCompiler());
        }
        if (spec instanceof ObjectiveCCompileSpec) {
            return castCompiler(createObjectiveCCompiler());
        }
        if (spec instanceof WindowsResourceCompileSpec) {
            return castCompiler(createWindowsResourceCompiler());
        }
        if (spec instanceof AssembleSpec) {
            return castCompiler(createAssembler());
        }
        if (spec instanceof LinkerSpec) {
            return castCompiler(createLinker());
        }
        if (spec instanceof StaticLibraryArchiverSpec) {
            return castCompiler(createStaticLibraryArchiver());
        }
        throw new IllegalArgumentException(String.format("Don't know how to compile from a spec of type %s.", spec.getClass().getSimpleName()));
    }

    protected final RuntimeException unavailableTool(String message) {
        return new RuntimeException(message);
    }

    protected Compiler<?> createCppCompiler() {
        throw unavailableTool("C++ compiler is not available");
    }

    protected Compiler<?> createCCompiler() {
        throw unavailableTool("C compiler is not available");
    }

    protected Compiler<?> createObjectiveCppCompiler() {
        throw unavailableTool("Obj-C++ compiler is not available");
    }

    protected Compiler<?> createObjectiveCCompiler() {
        throw unavailableTool("Obj-C compiler is not available");
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

    @SuppressWarnings("unchecked")
    private <T extends CompileSpec> Compiler<T> castCompiler(Compiler<?> compiler) {
        return (Compiler<T>) compiler;
    }
}
