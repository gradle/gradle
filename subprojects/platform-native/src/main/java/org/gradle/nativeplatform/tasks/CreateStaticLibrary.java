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
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.internal.DefaultStaticLibraryArchiverSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

/**
 * Assembles a static library from object files.
 */
public class CreateStaticLibrary extends DefaultTask implements ObjectFilesToBinary {

    private final ConfigurableFileCollection source;
    private final RegularFileProperty outputFile;
    private final ListProperty<String> staticLibArgs;
    private final Property<NativePlatform> targetPlatform;
    private final Property<NativeToolChain> toolChain;

    public CreateStaticLibrary() {
        ObjectFactory objectFactory = getProject().getObjects();
        this.source = getProject().files();
        this.outputFile = objectFactory.fileProperty();
        this.staticLibArgs = getProject().getObjects().listProperty(String.class);
        this.targetPlatform = objectFactory.property(NativePlatform.class);
        this.toolChain = objectFactory.property(NativeToolChain.class);
    }

    /**
     * The source object files to be passed to the archiver.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    @SkipWhenEmpty
    public FileCollection getSource() {
        return source;
    }

    /**
     * Adds a set of object files to be linked. <p> The provided source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    @Override
    public void source(Object source) {
        this.source.from(source);
    }

    @Inject
    public BuildOperationLoggerFactory getOperationLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    // TODO: Need to track version/implementation of ar tool.

    @TaskAction
    protected void link() {

        StaticLibraryArchiverSpec spec = new DefaultStaticLibraryArchiverSpec();
        spec.setTempDir(getTemporaryDir());
        spec.setOutputFile(getOutputFile().get().getAsFile());
        spec.objectFiles(getSource());
        spec.args(getStaticLibArgs().get());

        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());
        spec.setOperationLogger(operationLogger);

        Compiler<StaticLibraryArchiverSpec> compiler = createCompiler();
        WorkResult result = BuildOperationLoggingCompilerDecorator.wrap(compiler).execute(spec);
        setDidWork(result.getDidWork());
    }

    private Compiler<StaticLibraryArchiverSpec> createCompiler() {
        NativePlatformInternal targetPlatform = Cast.cast(NativePlatformInternal.class, this.targetPlatform.get());
        NativeToolChainInternal toolChain = Cast.cast(NativeToolChainInternal.class, getToolChain().get());
        PlatformToolProvider toolProvider = toolChain.select(targetPlatform);
        return toolProvider.newCompiler(StaticLibraryArchiverSpec.class);
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
     * The file where the output binary will be located.
     */
    @OutputFile
    public RegularFileProperty getOutputFile() {
        return outputFile;
    }

    /**
     * The file where the linked binary will be located.
     *
     * @since 4.5
     */
    @Internal
    public RegularFileProperty getBinaryFile() {
        return this.outputFile;
    }

    /**
     * <em>Additional</em> arguments passed to the archiver.
     *
     * @since 4.7
     */
    @Input
    public ListProperty<String> getStaticLibArgs() {
        return staticLibArgs;
    }

}
