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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.changedetection.changes.DiscoveredInputRecorder;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.nativeplatform.internal.incremental.DefaultHeaderDependenciesCollector;
import org.gradle.language.nativeplatform.internal.incremental.HeaderDependenciesCollector;
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompilerBuilder;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Compiles native source files into object files.
 */
@Incubating
public abstract class AbstractNativeCompileTask extends DefaultTask {
    private NativeToolChainInternal toolChain;
    private NativePlatformInternal targetPlatform;
    private boolean positionIndependentCode;
    private boolean debug;
    private boolean optimize;
    private final DirectoryProperty objectFileDir;
    private final ConfigurableFileCollection includes;
    private final ConfigurableFileCollection source;
    private final Map<String, String> macros = new LinkedHashMap<String, String>();
    private final ListProperty<String> compilerArgs;
    private ImmutableList<String> includePaths;
    private final RegularFileProperty headerDependenciesFile;

    public AbstractNativeCompileTask() {
        includes = getProject().files();
        source = getProject().files();
        objectFileDir = newOutputDirectory();
        compilerArgs = getProject().getObjects().listProperty(String.class);
        headerDependenciesFile = newInputFile();
        getOutputs().doNotCacheIf("Experimental native caching is not enabled", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task task) {
                return !Boolean.getBoolean("org.gradle.caching.native");
            }
        });
        getOutputs().doNotCacheIf("No header dependency analysis provided", new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return !getHeaderDependenciesFile().isPresent();
            }
        });
        dependsOn(includes);
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
    public void compile(IncrementalTaskInputs inputs) {
        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());
        NativeCompileSpec spec = createCompileSpec();
        spec.setTargetPlatform(targetPlatform);
        spec.setTempDir(getTemporaryDir());
        spec.setObjectFileDir(objectFileDir.get().getAsFile());
        spec.include(includes);
        spec.source(getSource());
        spec.setMacros(getMacros());
        spec.args(getCompilerArgs().get());
        spec.setPositionIndependentCode(isPositionIndependentCode());
        spec.setDebuggable(isDebuggable());
        spec.setOptimized(isOptimized());
        spec.setIncrementalCompile(inputs.isIncremental());
        spec.setDiscoveredInputRecorder((DiscoveredInputRecorder) inputs);
        spec.setOperationLogger(operationLogger);

        configureSpec(spec);

        PlatformToolProvider platformToolProvider = toolChain.select(targetPlatform);
        setDidWork(doCompile(spec, platformToolProvider).getDidWork());
    }

    protected void configureSpec(NativeCompileSpec spec) {
    }

    private <T extends NativeCompileSpec> WorkResult doCompile(T spec, PlatformToolProvider platformToolProvider) {
        Class<T> specType = Cast.uncheckedCast(spec.getClass());
        Compiler<T> baseCompiler = platformToolProvider.newCompiler(specType);
        HeaderDependenciesCollector headerDependenciesCollector = getHeaderDependenciesFile().isPresent() ? HeaderDependenciesCollector.NOOP : createDependenciesCollector();
        Compiler<T> incrementalCompiler = getIncrementalCompilerBuilder().createIncrementalCompiler(this, baseCompiler, toolChain, headerDependenciesCollector);
        Compiler<T> loggingCompiler = BuildOperationLoggingCompilerDecorator.wrap(incrementalCompiler);
        return loggingCompiler.execute(spec);
    }

    private DefaultHeaderDependenciesCollector createDependenciesCollector() {
        return new DefaultHeaderDependenciesCollector(((ProjectInternal) getProject()).getServices().get(DirectoryFileTreeFactory.class));
    }

    protected abstract NativeCompileSpec createCompileSpec();

    /**
     * The tool chain used for compilation.
     */
    @Internal
    public NativeToolChain getToolChain() {
        return toolChain;
    }

    public void setToolChain(NativeToolChain toolChain) {
        this.toolChain = (NativeToolChainInternal) toolChain;
    }

    /**
     * The platform being targeted.
     */
    @Nested
    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(NativePlatform targetPlatform) {
        this.targetPlatform = (NativePlatformInternal) targetPlatform;
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

    @Input
    @Optional
    protected Collection<String> getIncludePaths() {
        if (headerDependenciesFile.isPresent()) {
            return null; // => Include paths are handled by a different task
        }
        if (includePaths == null) {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            Set<File> roots = includes.getFiles();
            for (File root : roots) {
                builder.add(root.getAbsolutePath());
            }
            includePaths = builder.build();
        }
        return includePaths;
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
     * The file containing the header dependency analysis.
     *
     * @since 4.3
     */
    @Internal
    public RegularFileProperty getHeaderDependenciesFile() {
        return headerDependenciesFile;
    }

    /**
     * The set of dependent headers, read from {@link #getHeaderDependenciesFile()}}.
     *
     * This is used for up-to-date checks only.
     *
     * @since 4.3
     */
    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    protected FileCollection getHeaderDependencies() {
        final File inputFile = headerDependenciesFile.getAsFile().getOrNull();
        if (inputFile == null || !inputFile.isFile()) {
            return null;
        }

        return getFileCollectionFactory().create(new HeaderDependencies(inputFile));
    }

    private class HeaderDependencies implements MinimalFileSet {
        private final File inputFile;

        public HeaderDependencies(File inputFile) {
            this.inputFile = inputFile;
        }

        @Override
        public Set<File> getFiles() {
            try {
                return Files.readLines(inputFile, Charsets.UTF_8, new LineProcessor<Set<File>>() {
                    private Set<File> result = new HashSet<File>();

                    @Override
                    public boolean processLine(String line) throws IOException {
                        result.add(new File(line));
                        return true;
                    }

                    @Override
                    public Set<File> getResult() {
                        return result;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public String getDisplayName() {
            return "Header dependencies for " + AbstractNativeCompileTask.this.toString();
        }
    }
}
