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
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
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

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Assembles a static library from object files.
 */
@Incubating
public class CreateStaticLibrary extends DefaultTask implements ObjectFilesToBinary {

    private final ConfigurableFileCollection source;
    private NativeToolChainInternal toolChain;
    private NativePlatformInternal targetPlatform;
    private File outputFile;
    private List<String> staticLibArgs;

    public CreateStaticLibrary() {
        source = getProject().files();
        getInputs().property("outputType", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return NativeToolChainInternal.Identifier.identify(toolChain, targetPlatform);
            }
        });
    }

    /**
     * The source object files to be passed to the archiver.
     */
    @InputFiles
    @SkipWhenEmpty
    public FileCollection getSource() {
        return source;
    }

    /**
     * Adds a set of object files to be linked. <p> The provided source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void source(Object source) {
        this.source.from(source);
    }

    @Inject
    public BuildOperationLoggerFactory getOperationLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void link() {

        StaticLibraryArchiverSpec spec = new DefaultStaticLibraryArchiverSpec();
        spec.setTempDir(getTemporaryDir());
        spec.setOutputFile(getOutputFile());
        spec.objectFiles(getSource());
        spec.args(getStaticLibArgs());

        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());
        spec.setOperationLogger(operationLogger);

        Compiler<StaticLibraryArchiverSpec> compiler = Cast.uncheckedCast(toolChain.select(targetPlatform).newCompiler(spec.getClass()));
        WorkResult result = BuildOperationLoggingCompilerDecorator.wrap(compiler).execute(spec);
        setDidWork(result.getDidWork());
    }

    /**
     * The tool chain used for creating the static library.
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
     * The file where the output binary will be located.
     */
    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Additional arguments passed to the archiver.
     */
    @Input
    public List<String> getStaticLibArgs() {
        return staticLibArgs;
    }

    public void setStaticLibArgs(List<String> staticLibArgs) {
        this.staticLibArgs = staticLibArgs;
    }
}
