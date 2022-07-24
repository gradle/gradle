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
package org.gradle.language.rc.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
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
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompilerBuilder;
import org.gradle.language.rc.internal.DefaultWindowsResourceCompileSpec;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

import javax.inject.Inject;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Compiles Windows Resource scripts into .res files.
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
public class WindowsResourceCompile extends DefaultTask {

    private final Property<NativePlatform> targetPlatform;
    private final Property<NativeToolChain> toolChain;
    private File outputDir;
    private ConfigurableFileCollection includes;
    private ConfigurableFileCollection source;
    private Map<String, String> macros = new LinkedHashMap<String, String>();
    private final ListProperty<String> compilerArgs;
    private final IncrementalCompilerBuilder.IncrementalCompiler incrementalCompiler;

    public WindowsResourceCompile() {
        ObjectFactory objectFactory = getProject().getObjects();
        includes = getProject().files();
        source = getProject().files();
        this.compilerArgs = getProject().getObjects().listProperty(String.class);
        this.targetPlatform = objectFactory.property(NativePlatform.class);
        this.toolChain = objectFactory.property(NativeToolChain.class);
        incrementalCompiler = getIncrementalCompilerBuilder().newCompiler(this, source, includes, macros, Providers.FALSE);
        getInputs().property("outputType", new Callable<String>() {
            @Override
            public String call() {
                NativeToolChainInternal nativeToolChain = (NativeToolChainInternal) toolChain.get();
                NativePlatformInternal nativePlatform = (NativePlatformInternal) targetPlatform.get();
                return NativeToolChainInternal.Identifier.identify(nativeToolChain, nativePlatform);
            }
        });
    }

    @Inject
    public IncrementalCompilerBuilder getIncrementalCompilerBuilder() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public BuildOperationLoggerFactory getOperationLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void compile(InputChanges inputs) {
        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());

        NativeCompileSpec spec = new DefaultWindowsResourceCompileSpec();
        spec.setTempDir(getTemporaryDir());
        spec.setObjectFileDir(getOutputDir());
        spec.include(getIncludes());
        spec.source(getSource());
        spec.setMacros(getMacros());
        spec.args(getCompilerArgs().get());
        spec.setIncrementalCompile(inputs.isIncremental());
        spec.setOperationLogger(operationLogger);

        NativeToolChainInternal nativeToolChain = (NativeToolChainInternal) toolChain.get();
        NativePlatformInternal nativePlatform = (NativePlatformInternal) targetPlatform.get();
        PlatformToolProvider platformToolProvider = nativeToolChain.select(nativePlatform);
        WorkResult result = doCompile(spec, platformToolProvider);
        setDidWork(result.getDidWork());
    }

    private <T extends NativeCompileSpec> WorkResult doCompile(T spec, PlatformToolProvider platformToolProvider) {
        Class<T> specType = Cast.uncheckedCast(spec.getClass());
        Compiler<T> baseCompiler = platformToolProvider.newCompiler(specType);
        Compiler<T> incrementalCompiler = this.incrementalCompiler.createCompiler(baseCompiler);
        Compiler<T> loggingCompiler = BuildOperationLoggingCompilerDecorator.wrap(incrementalCompiler);
        return CompilerUtil.castCompiler(loggingCompiler).execute(spec);
    }

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
     * The directory where object files will be generated.
     */
    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Returns the header directories to be used for compilation.
     */
    @Incremental
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
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
     * Returns the source files to be compiled.
     */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
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
        this.macros = macros;
    }

    /**
     * <em>Additional</em> arguments to provide to the compiler.
     *
     * @since 5.1
     */
    @Input
    public ListProperty<String> getCompilerArgs() {
        return compilerArgs;
    }


    /**
     * The set of dependent headers. This is used for up-to-date checks only.
     *
     * @since 4.5
     */
    @Incremental
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    protected FileCollection getHeaderDependencies() {
        return incrementalCompiler.getHeaderFiles();
    }
}
