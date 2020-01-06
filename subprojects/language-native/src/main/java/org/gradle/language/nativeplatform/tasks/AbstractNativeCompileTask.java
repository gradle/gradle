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
package org.gradle.language.nativeplatform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.TaskFileVarFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompilerBuilder;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

import javax.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compiles native source files into object files.
 */
public abstract class AbstractNativeCompileTask extends DefaultTask {
    private final Property<NativePlatform> targetPlatform;
    private final Property<NativeToolChain> toolChain;
    private boolean positionIndependentCode;
    private boolean debug;
    private boolean optimize;
    private final DirectoryProperty objectFileDir;
    private final ConfigurableFileCollection includes;
    private final ConfigurableFileCollection systemIncludes;
    private final ConfigurableFileCollection source;
    private final Map<String, String> macros = new LinkedHashMap<String, String>();
    private final ListProperty<String> compilerArgs;
    private final IncrementalCompilerBuilder.IncrementalCompiler incrementalCompiler;

    public AbstractNativeCompileTask() {
        ObjectFactory objectFactory = getProject().getObjects();
        this.includes = getProject().files();
        this.systemIncludes = getProject().files();
        dependsOn(includes);
        dependsOn(systemIncludes);

        this.source = getTaskFileVarFactory().newInputFileCollection(this);
        this.objectFileDir = objectFactory.directoryProperty();
        this.compilerArgs = getProject().getObjects().listProperty(String.class);
        this.targetPlatform = objectFactory.property(NativePlatform.class);
        this.toolChain = objectFactory.property(NativeToolChain.class);
        this.incrementalCompiler = getIncrementalCompilerBuilder().newCompiler(this, source, includes.plus(systemIncludes), macros, toolChain.map(new Transformer<Boolean, NativeToolChain>() {
            @Override
            public Boolean transform(NativeToolChain nativeToolChain) {
                return nativeToolChain instanceof Gcc || nativeToolChain instanceof Clang;
            }
        }));
    }

    @Inject
    protected TaskFileVarFactory getTaskFileVarFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected IncrementalCompilerBuilder getIncrementalCompilerBuilder() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected BuildOperationLoggerFactory getOperationLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileCollectionFactory getFileCollectionFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    protected void compile(InputChanges inputs) {
        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());
        NativeCompileSpec spec = createCompileSpec();
        spec.setTargetPlatform(targetPlatform.get());
        spec.setTempDir(getTemporaryDir());
        spec.setObjectFileDir(objectFileDir.get().getAsFile());
        spec.include(includes);
        spec.systemInclude(systemIncludes);
        spec.source(getSource());
        spec.setMacros(getMacros());
        spec.args(getCompilerArgs().get());
        spec.setPositionIndependentCode(isPositionIndependentCode());
        spec.setDebuggable(isDebuggable());
        spec.setOptimized(isOptimized());
        spec.setIncrementalCompile(inputs.isIncremental());
        spec.setOperationLogger(operationLogger);

        configureSpec(spec);

        NativeToolChainInternal nativeToolChain = (NativeToolChainInternal) toolChain.get();
        NativePlatformInternal nativePlatform = (NativePlatformInternal) targetPlatform.get();
        PlatformToolProvider platformToolProvider = nativeToolChain.select(nativePlatform);
        setDidWork(doCompile(spec, platformToolProvider).getDidWork());
    }

    protected void configureSpec(NativeCompileSpec spec) {
    }

    private <T extends NativeCompileSpec> WorkResult doCompile(T spec, PlatformToolProvider platformToolProvider) {
        Class<T> specType = Cast.uncheckedCast(spec.getClass());
        Compiler<T> baseCompiler = platformToolProvider.newCompiler(specType);
        Compiler<T> incrementalCompiler = this.incrementalCompiler.createCompiler(baseCompiler);
        Compiler<T> loggingCompiler = BuildOperationLoggingCompilerDecorator.wrap(incrementalCompiler);
        return loggingCompiler.execute(spec);
    }

    protected abstract NativeCompileSpec createCompileSpec();

    /**
     * The tool chain used for compilation.
     *
     * @since 4.7
     */
    @Internal
    public Property<NativeToolChain> getToolChain() {
        return toolChain;
    }

    /**
     * The platform being compiled for.
     *
     * @since 4.7
     */
    @Nested
    public Property<NativePlatform> getTargetPlatform() {
        return targetPlatform;
    }

    /**
     * Should the compiler generate position independent code?
     */
    @Input
    public boolean isPositionIndependentCode() {
        return positionIndependentCode;
    }

    public void setPositionIndependentCode(boolean positionIndependentCode) {
        this.positionIndependentCode = positionIndependentCode;
    }

    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.3
     */
    @Input
    public boolean isDebuggable() {
        return debug;
    }

    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.3
     */
    public void setDebuggable(boolean debug) {
        this.debug = debug;
    }

    /**
     * Should the compiler generate optimized code?
     *
     * @since 4.3
     */
    @Input
    public boolean isOptimized() {
        return optimize;
    }

    /**
     * Should the compiler generate optimized code?
     *
     * @since 4.3
     */
    public void setOptimized(boolean optimize) {
        this.optimize = optimize;
    }

    /**
     * The directory where object files will be generated.
     *
     * @since 4.3
     */
    @OutputDirectory
    public DirectoryProperty getObjectFileDir() {
        return objectFileDir;
    }

    /**
     * Returns the header directories to be used for compilation.
     */
    @Internal("The paths for include directories are tracked via the includePaths property, the contents are tracked via discovered inputs")
    public ConfigurableFileCollection getIncludes() {
        return includes;
    }

    /**
     * Add directories where the compiler should search for header files.
     */
    public void includes(Object includeRoots) {
        includes.from(includeRoots);
    }

    /**
     * Returns the system include directories to be used for compilation.
     *
     * @since 4.8
     */
    @Internal("The paths for include directories are tracked via the includePaths property, the contents are tracked via discovered inputs")
    public ConfigurableFileCollection getSystemIncludes() {
        return systemIncludes;
    }

    /**
     * Returns the source files to be compiled.
     */
    @SkipWhenEmpty
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getSource() {
        return source;
    }

    /**
     * Adds a set of source files to be compiled. The provided sourceFiles object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void source(Object sourceFiles) {
        source.from(sourceFiles);
    }

    /**
     * Macros that should be defined for the compiler.
     */
    @Input
    public Map<String, String> getMacros() {
        return macros;
    }

    public void setMacros(Map<String, String> macros) {
        this.macros.clear();
        this.macros.putAll(macros);
    }

    /**
     * <em>Additional</em> arguments to provide to the compiler.
     *
     * @since 4.3
     */
    @Input
    public ListProperty<String> getCompilerArgs() {
        return compilerArgs;
    }

    /**
     * The set of dependent headers. This is used for up-to-date checks only.
     *
     * @since 4.3
     */
    @Incremental
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    protected FileCollection getHeaderDependencies() {
        return incrementalCompiler.getHeaderFiles();
    }
}
