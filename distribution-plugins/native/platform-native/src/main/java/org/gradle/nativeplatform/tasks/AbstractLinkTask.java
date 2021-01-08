/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.nativeplatform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
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
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

/**
 * Base task for linking a native binary from object files and libraries.
 */
public abstract class AbstractLinkTask extends DefaultTask implements ObjectFilesToBinary {
    private final RegularFileProperty linkedFile;
    private final DirectoryProperty destinationDirectory;
    private final ListProperty<String> linkerArgs;
    private final ConfigurableFileCollection source;
    private final ConfigurableFileCollection libs;
    private final Property<Boolean> debuggable;
    private final Property<NativePlatform> targetPlatform;
    private final Property<NativeToolChain> toolChain;

    public AbstractLinkTask() {
        final ObjectFactory objectFactory = getProject().getObjects();
        this.libs = getProject().files();
        this.source = getProject().files();
        this.linkedFile = objectFactory.fileProperty();
        this.destinationDirectory = objectFactory.directoryProperty();
        destinationDirectory.set(linkedFile.getLocationOnly().map(regularFile -> {
            // TODO: Get rid of destinationDirectory entirely and replace it with a
            // collection of link outputs
            DirectoryProperty dirProp = objectFactory.directoryProperty();
            dirProp.set(regularFile.getAsFile().getParentFile());
            return dirProp.get();
        }));
        this.linkerArgs = getProject().getObjects().listProperty(String.class);
        this.debuggable = objectFactory.property(Boolean.class).value(false);
        this.targetPlatform = objectFactory.property(NativePlatform.class);
        this.toolChain = objectFactory.property(NativeToolChain.class);
    }

    /**
     * The tool chain used for linking.
     *
     * @since 4.7
     */
    @Internal
    public Property<NativeToolChain> getToolChain() {
        return toolChain;
    }

    /**
     * The platform being linked for.
     *
     * @since 4.7
     */
    @Nested
    public Property<NativePlatform> getTargetPlatform() {
        return targetPlatform;
    }

    /**
     * Include the destination directory as an output, to pick up auxiliary files produced alongside the main output file
     *
     * @since 4.7
     */
    @OutputDirectory
    public DirectoryProperty getDestinationDirectory() {
        return destinationDirectory;
    }

    /**
     * The file where the linked binary will be located.
     *
     * @since 4.7
     */
    @OutputFile
    public RegularFileProperty getLinkedFile() {
        return linkedFile;
    }

    /**
     * <em>Additional</em> arguments passed to the linker.
     *
     * @since 4.3
     */
    @Input
    public ListProperty<String> getLinkerArgs() {
        return linkerArgs;
    }

    /**
     * Create a debuggable binary?
     *
     * @since 4.7
     */
    @Internal
    public boolean isDebuggable() {
        return debuggable.get();
    }

    /**
     * Create a debuggable binary?
     *
     * @since 4.7
     */
    @Input
    public Property<Boolean> getDebuggable() {
        return debuggable;
    }

    /**
     * The source object files to be passed to the linker.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    @SkipWhenEmpty
    public ConfigurableFileCollection getSource() {
        return source;
    }

    public void setSource(FileCollection source) {
        this.source.setFrom(source);
    }

    /**
     * The library files to be passed to the linker.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    public ConfigurableFileCollection getLibs() {
        return libs;
    }

    public void setLibs(FileCollection libs) {
        this.libs.setFrom(libs);
    }

    /**
     * Adds a set of object files to be linked. The provided source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    @Override
    public void source(Object source) {
        this.source.from(source);
    }

    /**
     * Adds a set of library files to be linked. The provided libs object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void lib(Object libs) {
        this.libs.from(libs);
    }

    /**
     * The linker used, including the type and the version.
     *
     * @since 4.7
     */
    @Nested
    protected CompilerVersion getCompilerVersion() {
        return ((VersionAwareCompiler)createCompiler()).getVersion();
    }

    @Inject
    protected BuildOperationLoggerFactory getOperationLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator takes care of injection");
    }

    @TaskAction
    protected void link() {
        boolean cleanedOutputs = StaleOutputCleaner.cleanOutputs(
            getDeleter(),
            getOutputs().getPreviousOutputFiles(),
            getDestinationDirectory().get().getAsFile()
        );

        if (getSource().isEmpty()) {
            setDidWork(cleanedOutputs);
            return;
        }

        LinkerSpec spec = createLinkerSpec();
        spec.setTargetPlatform(getTargetPlatform().get());
        spec.setTempDir(getTemporaryDir());
        spec.setOutputFile(getLinkedFile().get().getAsFile());

        spec.objectFiles(getSource());
        spec.libraries(getLibs());
        spec.args(getLinkerArgs().get());
        spec.setDebuggable(getDebuggable().get());

        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());
        spec.setOperationLogger(operationLogger);

        Compiler<LinkerSpec> compiler = createCompiler();
        compiler = BuildOperationLoggingCompilerDecorator.wrap(compiler);
        WorkResult result = compiler.execute(spec);
        setDidWork(result.getDidWork() || cleanedOutputs);
    }

    @SuppressWarnings("unchecked")
    private Compiler<LinkerSpec> createCompiler() {
        NativePlatformInternal targetPlatform = Cast.cast(NativePlatformInternal.class, this.targetPlatform.get());
        NativeToolChainInternal toolChain = Cast.cast(NativeToolChainInternal.class, getToolChain().get());
        PlatformToolProvider toolProvider = toolChain.select(targetPlatform);
        Class<LinkerSpec> linkerSpecType = (Class<LinkerSpec>) createLinkerSpec().getClass();
        return toolProvider.newCompiler(linkerSpecType);
    }

    protected abstract LinkerSpec createLinkerSpec();

}
