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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
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
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.base.compile.CompilerVersion;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.VersionAwareCompiler;
import org.gradle.language.swift.SwiftVersion;
import org.gradle.language.swift.tasks.internal.DefaultSwiftCompileSpec;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.swift.IncrementalSwiftCompiler;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final Property<String> moduleName;
    private final RegularFileProperty moduleFile;
    private final ConfigurableFileCollection modules;
    private final ListProperty<String> compilerArgs;
    private final DirectoryProperty objectFileDir;
    private final ConfigurableFileCollection source;
    private final Property<SwiftVersion> sourceCompatibility;
    private final ListProperty<String> macros;
    private final Property<Boolean> debug;
    private final Property<Boolean> optimize;

    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;

    @Inject
    public SwiftCompile(CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory) {
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;

        ObjectFactory objectFactory = getProject().getObjects();
        this.moduleName = objectFactory.property(String.class);
        this.moduleFile = newOutputFile();
        this.modules = getProject().files();
        this.compilerArgs = objectFactory.listProperty(String.class);
        this.objectFileDir = newOutputDirectory();
        this.source = getProject().files();
        this.sourceCompatibility = objectFactory.property(SwiftVersion.class);
        this.macros = objectFactory.listProperty(String.class);
        this.debug = objectFactory.property(Boolean.class);
        this.optimize = objectFactory.property(Boolean.class);
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
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getSource() {
        return source;
    }

    /**
     * Macros that should be defined for the compiler.
     *
     * <p>Macros do not have values in Swift; they are either present or absent.</p>
     *
     * @since 4.6
     */
    @Input
    public ListProperty<String> getMacros() {
        return macros;
    }

    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.6
     */
    @Input
    public Property<Boolean> isDebuggable() {
        return debug;
    }

    /**
     * Should the compiler generate optimized code?
     *
     * @since 4.6
     */
    @Input
    public Property<Boolean> isOptimized() {
        return optimize;
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
     * Returns the Swift language level to use to compile the source files.
     *
     * @since 4.6
     */
    @Input
    public Property<SwiftVersion> getSourceCompatibility() {
        return sourceCompatibility;
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
    void compile(IncrementalTaskInputs inputs) {
        final List<File> removedFiles = Lists.newArrayList();
        final Set<File> changedFiles = Sets.newHashSet();
        boolean isIncremental = inputs.isIncremental();

        // TODO: This should become smarter and move into the compiler infrastructure instead
        // of the task, similar to how the other native languages are done.
        // For now, this does a rudimentary incremental build analysis by looking at
        // which files changed and marking the compilation incremental or not.
        if (isIncremental) {
            inputs.outOfDate(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails inputFileDetails) {
                    if (inputFileDetails.isModified()) {
                        changedFiles.add(inputFileDetails.getFile());
                    }
                }
            });
            inputs.removed(new Action<InputFileDetails>() {
                @Override
                public void execute(InputFileDetails removed) {
                    removedFiles.add(removed.getFile());
                }
            });

            Set<File> allSourceFiles = getSource().getFiles();
            if (!allSourceFiles.containsAll(changedFiles)) {
                // If a non-source file changed, the compilation cannot be incremental
                // due to the way the Swift compiler detects changes from other modules
                isIncremental = false;
            }
        }

        BuildOperationLogger operationLogger = getServices().get(BuildOperationLoggerFactory.class).newOperationLogger(getName(), getTemporaryDir());

        SwiftCompileSpec spec = createSpec(operationLogger, isIncremental, changedFiles, removedFiles);

        PlatformToolProvider platformToolProvider = toolChain.select(targetPlatform);
        Compiler<SwiftCompileSpec> baseCompiler = new IncrementalSwiftCompiler(platformToolProvider.newCompiler(SwiftCompileSpec.class), getOutputs(), compilerOutputFileNamingSchemeFactory);
        Compiler<SwiftCompileSpec> loggingCompiler = BuildOperationLoggingCompilerDecorator.wrap(baseCompiler);
        WorkResult result = loggingCompiler.execute(spec);
        setDidWork(result.getDidWork());
    }

    private SwiftCompileSpec createSpec(BuildOperationLogger operationLogger, boolean isIncremental, Collection<File> changedFiles, Collection<File> removedFiles) {
        SwiftCompileSpec spec = new DefaultSwiftCompileSpec();
        spec.setModuleName(moduleName.getOrNull());
        spec.setModuleFile(moduleFile.get().getAsFile());
        for (File file : modules.getFiles()) {
            if (file.isFile()) {
                spec.include(file.getParentFile());
            } else {
                spec.include(file);
            }
        }

        spec.setTargetPlatform(targetPlatform);
        spec.setTempDir(getTemporaryDir());
        spec.setObjectFileDir(objectFileDir.get().getAsFile());
        spec.source(getSource());
        spec.setRemovedSourceFiles(removedFiles);
        spec.setChangedFiles(changedFiles);

        // Convert Swift-like macros to a Map like NativeCompileSpec expects
        Map<String, String> macros = new LinkedHashMap<String, String>();
        for (String macro : getMacros().get()) {
            macros.put(macro, null);
        }
        spec.setMacros(macros);
        spec.args(getCompilerArgs().get());
        spec.setDebuggable(isDebuggable().get());
        spec.setOptimized(isOptimized().get());
        spec.setIncrementalCompile(isIncremental);
        spec.setOperationLogger(operationLogger);
        spec.setSourceCompatibility(sourceCompatibility.get());
        return spec;
    }
}
