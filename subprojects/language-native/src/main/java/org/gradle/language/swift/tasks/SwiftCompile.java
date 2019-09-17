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
import org.gradle.api.DefaultTask;
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
import org.gradle.internal.Cast;
import org.gradle.internal.file.Deleter;
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
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

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
@CacheableTask
public class SwiftCompile extends DefaultTask {
    private final Property<String> moduleName;
    private final RegularFileProperty moduleFile;
    private final ConfigurableFileCollection modules;
    private final ListProperty<String> compilerArgs;
    private final DirectoryProperty objectFileDir;
    private final ConfigurableFileCollection source;
    private final Property<SwiftVersion> sourceCompatibility;
    private final ListProperty<String> macros;
    private final Property<Boolean> debuggable;
    private final Property<Boolean> optimize;
    private final Property<NativePlatform> targetPlatform;
    private final Property<NativeToolChain> toolChain;

    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final Deleter deleter;

    @Inject
    public SwiftCompile(
        CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory,
        Deleter deleter
    ) {
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.deleter = deleter;

        ObjectFactory objectFactory = getProject().getObjects();
        this.moduleName = objectFactory.property(String.class);
        this.moduleFile = objectFactory.fileProperty();
        this.modules = getProject().files();
        this.compilerArgs = objectFactory.listProperty(String.class);
        this.objectFileDir = objectFactory.directoryProperty();
        this.source = getProject().files();
        this.sourceCompatibility = objectFactory.property(SwiftVersion.class);
        this.macros = objectFactory.listProperty(String.class);
        this.debuggable = objectFactory.property(Boolean.class).value(false);
        this.optimize = objectFactory.property(Boolean.class).value(false);
        this.targetPlatform = objectFactory.property(NativePlatform.class);
        this.toolChain = objectFactory.property(NativeToolChain.class);
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
     * @since 4.7
     */
    @Input
    public ListProperty<String> getMacros() {
        return macros;
    }

    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.7
     */
    @Internal
    public boolean isDebuggable() {
        return debuggable.get();
    }

    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.7
     */
    @Input
    public Property<Boolean> getDebuggable() {
        return debuggable;
    }

    /**
     * Should the compiler generate debuggable code?
     *
     * @since 4.7
     */
    @Internal
    public boolean isOptimized() {
        return optimize.get();
    }

    /**
     * Should the compiler generate optimized code?
     *
     * @since 4.7
     */
    @Input
    public Property<Boolean> getOptimized() {
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
        return ((VersionAwareCompiler)createCompiler()).getVersion();
    }

    private Compiler<SwiftCompileSpec> createCompiler() {
        NativePlatformInternal targetPlatform = Cast.cast(NativePlatformInternal.class, this.targetPlatform.get());
        NativeToolChainInternal toolChain = Cast.cast(NativeToolChainInternal.class, getToolChain().get());
        PlatformToolProvider toolProvider = toolChain.select(targetPlatform);
        return toolProvider.newCompiler(SwiftCompileSpec.class);
    }

    @TaskAction
    protected void compile(InputChanges inputs) {
        final List<File> removedFiles = Lists.newArrayList();
        final Set<File> changedFiles = Sets.newHashSet();
        boolean isIncremental = inputs.isIncremental();

        // TODO: This should become smarter and move into the compiler infrastructure instead
        //   of the task, similar to how the other native languages are done.
        //   For now, this does a rudimentary incremental build analysis by looking at
        //   which files changed .
        if (isIncremental) {
            for (FileChange fileChange : inputs.getFileChanges(getSource())) {
                if (fileChange.getChangeType() == ChangeType.REMOVED) {
                    removedFiles.add(fileChange.getFile());
                } else {
                    changedFiles.add(fileChange.getFile());
                }
            }
        }

        BuildOperationLogger operationLogger = getServices().get(BuildOperationLoggerFactory.class).newOperationLogger(getName(), getTemporaryDir());

        NativePlatformInternal targetPlatform = Cast.cast(NativePlatformInternal.class, this.targetPlatform.get());
        SwiftCompileSpec spec = createSpec(operationLogger, isIncremental, changedFiles, removedFiles, targetPlatform);
        Compiler<SwiftCompileSpec> baseCompiler = new IncrementalSwiftCompiler(
            createCompiler(),
            getOutputs(),
            compilerOutputFileNamingSchemeFactory,
            deleter
        );
        Compiler<SwiftCompileSpec> loggingCompiler = BuildOperationLoggingCompilerDecorator.wrap(baseCompiler);
        WorkResult result = loggingCompiler.execute(spec);
        setDidWork(result.getDidWork());
    }

    private SwiftCompileSpec createSpec(BuildOperationLogger operationLogger, boolean isIncremental, Collection<File> changedFiles, Collection<File> removedFiles, NativePlatformInternal targetPlatform) {
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
        Map<String, String> macros = new LinkedHashMap<>();
        for (String macro : getMacros().get()) {
            macros.put(macro, null);
        }
        spec.setMacros(macros);
        spec.args(getCompilerArgs().get());
        spec.setDebuggable(getDebuggable().get());
        spec.setOptimized(getOptimized().get());
        spec.setIncrementalCompile(isIncremental);
        spec.setOperationLogger(operationLogger);
        spec.setSourceCompatibility(sourceCompatibility.get());
        return spec;
    }
}
