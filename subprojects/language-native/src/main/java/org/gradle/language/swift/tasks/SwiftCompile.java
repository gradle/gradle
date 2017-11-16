/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.base.compile.CompilerVersion;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.VersionAwareCompiler;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.language.swift.internal.DefaultSwiftCompileSpec;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compiles Swift source files into object files.
 *
 * @since 4.1
 */
@Incubating
@CacheableTask
public class SwiftCompile extends DefaultTask {
    private NativeToolChainInternal toolChain;
    private NativePlatformInternal targetPlatform;
    private boolean debug;
    private boolean optimize;
    private final Property<String> moduleName;
    private final RegularFileProperty moduleFile;
    private final ConfigurableFileCollection modules;
    private final ListProperty<String> compilerArgs;
    private final DirectoryProperty objectFileDir;
    private final ConfigurableFileCollection source;
    private final Map<String, String> macros = new LinkedHashMap<String, String>();

    public SwiftCompile() {
        source = getProject().files();
        compilerArgs = getProject().getObjects().listProperty(String.class);
        objectFileDir = newOutputDirectory();
        moduleName = getProject().getObjects().property(String.class);
        moduleFile = newOutputFile();
        modules = getProject().files();
    }

    /**
     * The tool chain used for compilation.
     *
     * @since 4.4
     */
    @Internal
    public NativeToolChain getToolChain() {
        return toolChain;
    }

    /**
     * Sets the tool chain to use for compilation.
     *
     * @since 4.4
     */
    public void setToolChain(NativeToolChain toolChain) {
        this.toolChain = (NativeToolChainInternal) toolChain;
    }

    /**
     * The platform being compiled for.
     *
     * @since 4.4
     */
    @Nested
    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    /**
     * Sets the platform being compiled for.
     *
     * @since 4.4
     */
    public void setTargetPlatform(NativePlatform targetPlatform) {
        this.targetPlatform = (NativePlatformInternal) targetPlatform;
    }

    /**
     * Returns the source files to be compiled.
     *
     * @since 4.4
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getSource() {
        return source;
    }

    /**
     * Macros that should be defined for the compiler.
     *
     * @since 4.4
     */
    @Input
    public Map<String, String> getMacros() {
        return macros;
    }

    /**
     * Sets the macros that should be defined when compiling.
     *
     * @since 4.4
     */
    public void setMacros(Map<String, String> macros) {
        this.macros.clear();
        this.macros.putAll(macros);
    }

    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.4
     */
    @Input
    public boolean isDebuggable() {
        return debug;
    }

    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.4
     */
    public void setDebuggable(boolean debug) {
        this.debug = debug;
    }

    /**
     * Should the compiler generate optimized code?
     *
     * @since 4.4
     */
    @Input
    public boolean isOptimized() {
        return optimize;
    }

    /**
     * Should the compiler generate optimized code?
     *
     * @since 4.4
     */
    public void setOptimized(boolean optimize) {
        this.optimize = optimize;
    }

    /**
     * <em>Additional</em> arguments to provide to the compiler.
     *
     * @since 4.4
     */
    @Input
    public ListProperty<String> getCompilerArgs() {
        return compilerArgs;
    }

    /**
     * The directory where object files will be generated.
     *
     * @since 4.4
     */
    @OutputDirectory
    public DirectoryProperty getObjectFileDir() {
        return objectFileDir;
    }

    /**
     * The location to write the Swift module file to.
     *
     * @since 4.4
     */
    @OutputFile
    public RegularFileProperty getModuleFile() {
        return moduleFile;
    }

    /**
     * The name of the module to produce.
     */
    @Optional
    @Input
    public Property<String> getModuleName() {
        return moduleName;
    }

    /**
     * The modules required to compile the source.
     *
     * @since 4.4
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public ConfigurableFileCollection getModules() {
        return modules;
    }

    /**
     * The compiler used, including the type and the version.
     *
     * @since 4.4
     */
    @Nested
    protected CompilerVersion getCompilerVersion() {
        NativeToolChainInternal toolChain = (NativeToolChainInternal) getToolChain();
        NativePlatformInternal targetPlatform = (NativePlatformInternal) getTargetPlatform();
        PlatformToolProvider toolProvider = toolChain.select(targetPlatform);
        VersionAwareCompiler<?> compiler = (VersionAwareCompiler<?>) toolProvider.newCompiler(SwiftCompileSpec.class);
        return compiler.getVersion();
    }

    @TaskAction
    void compile() {
        SimpleStaleClassCleaner cleaner = new SimpleStaleClassCleaner(getOutputs());
        cleaner.setDestinationDir(getObjectFileDir().getAsFile().get());
        cleaner.execute();

        if (getSource().isEmpty()) {
            setDidWork(cleaner.getDidWork());
            return;
        }

        BuildOperationLogger operationLogger = getServices().get(BuildOperationLoggerFactory.class).newOperationLogger(getName(), getTemporaryDir());

        SwiftCompileSpec spec = new DefaultSwiftCompileSpec();
        spec.setModuleName(moduleName.getOrNull());
        spec.setModuleFile(moduleFile.get().getAsFile());
        for (File file : modules.getFiles()) {
            spec.include(file.getParentFile());
        }

        spec.setTargetPlatform(targetPlatform);
        spec.setTempDir(getTemporaryDir());
        spec.setObjectFileDir(objectFileDir.get().getAsFile());
        spec.source(getSource());
        spec.setMacros(getMacros());
        spec.args(getCompilerArgs().get());
        spec.setDebuggable(isDebuggable());
        spec.setOptimized(isOptimized());
        spec.setIncrementalCompile(false);
        spec.setOperationLogger(operationLogger);

        PlatformToolProvider platformToolProvider = toolChain.select(targetPlatform);
        Compiler<SwiftCompileSpec> baseCompiler = platformToolProvider.newCompiler(SwiftCompileSpec.class);
        Compiler<SwiftCompileSpec> loggingCompiler = BuildOperationLoggingCompilerDecorator.wrap(baseCompiler);
        WorkResult result = loggingCompiler.execute(spec);
        setDidWork(result.getDidWork());
    }
}
