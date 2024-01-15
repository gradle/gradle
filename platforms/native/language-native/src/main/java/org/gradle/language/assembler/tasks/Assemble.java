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
package org.gradle.language.assembler.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
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
import org.gradle.internal.file.Deleter;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.assembler.internal.DefaultAssembleSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Translates Assembly language source files into object files.
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class Assemble extends DefaultTask {
    private ConfigurableFileCollection source;
    private ConfigurableFileCollection includes;
    private final Property<NativePlatform> targetPlatform;
    private final Property<NativeToolChain> toolChain;
    private File objectFileDir;
    private List<String> assemblerArgs;

    @Inject
    public Assemble() {
        ObjectFactory objectFactory = getProject().getObjects();
        source = getProject().files();
        includes = getProject().files();
        this.targetPlatform = objectFactory.property(NativePlatform.class);
        this.toolChain = objectFactory.property(NativeToolChain.class);
        getInputs().property("outputType", new Callable<String>() {
            @Override
            public String call() throws Exception {
                NativeToolChainInternal nativeToolChain = (NativeToolChainInternal) toolChain.get();
                NativePlatformInternal nativePlatform = (NativePlatformInternal) targetPlatform.get();
                return NativeToolChainInternal.Identifier.identify(nativeToolChain, nativePlatform);
            }
        });
    }

    @Inject
    public BuildOperationLoggerFactory getOperationLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator takes care of injection");
    }

    @TaskAction
    public void assemble() {
        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());

        boolean cleanedOutputs = StaleOutputCleaner.cleanOutputs(
            getDeleter(),
            getOutputs().getPreviousOutputFiles(),
            getObjectFileDir()
        );

        DefaultAssembleSpec spec = new DefaultAssembleSpec();
        spec.setTempDir(getTemporaryDir());

        spec.setObjectFileDir(getObjectFileDir());
        spec.source(getSource());
        spec.include(getIncludes());
        spec.args(getAssemblerArgs());
        spec.setOperationLogger(operationLogger);

        NativeToolChainInternal nativeToolChain = (NativeToolChainInternal) toolChain.get();
        NativePlatformInternal nativePlatform = (NativePlatformInternal) targetPlatform.get();
        Compiler<AssembleSpec> compiler = nativeToolChain.select(nativePlatform).newCompiler(AssembleSpec.class);
        WorkResult result = BuildOperationLoggingCompilerDecorator.wrap(compiler).execute(spec);
        setDidWork(result.getDidWork() || cleanedOutputs);
    }

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getSource() {
        return source;
    }

    /**
     * Adds a set of assembler sources files to be translated. The provided sourceFiles object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void source(Object sourceFiles) {
        source.from(sourceFiles);
    }

    /**
     * Additional arguments to provide to the assembler.
     */
    @Input
    public List<String> getAssemblerArgs() {
        return assemblerArgs;
    }

    public void setAssemblerArgs(List<String> assemblerArgs) {
        this.assemblerArgs = assemblerArgs;
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
    public File getObjectFileDir() {
        return objectFileDir;
    }

    public void setObjectFileDir(File objectFileDir) {
        this.objectFileDir = objectFileDir;
    }

    /**
     * Returns the header directories to be used for compilation.
     *
     * @since 4.4
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    public ConfigurableFileCollection getIncludes() {
        return includes;
    }

    /**
     * Add directories where the compiler should search for header files.
     *
     * @since 4.4
     */
    public void includes(Object includeRoots) {
        includes.from(includeRoots);
    }
}
