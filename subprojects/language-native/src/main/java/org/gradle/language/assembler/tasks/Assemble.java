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

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.assembler.internal.DefaultAssembleSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Translates Assembly language source files into object files.
 */
@Incubating
public class Assemble extends DefaultTask {
    private FileCollection source;
    private ConfigurableFileCollection includes;
    private NativeToolChainInternal toolChain;
    private NativePlatformInternal targetPlatform;
    private File objectFileDir;
    private List<String> assemblerArgs;

    @Inject
    public Assemble() {
        source = getProject().files();
        includes = getProject().files();
        getInputs().property("outputType", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return NativeToolChainInternal.Identifier.identify(toolChain, targetPlatform);
            }
        });
    }

    @Inject
    public BuildOperationLoggerFactory getOperationLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void assemble() {
        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());
        SimpleStaleClassCleaner cleaner = new SimpleStaleClassCleaner(getOutputs());
        cleaner.setDestinationDir(getObjectFileDir());
        cleaner.execute();

        DefaultAssembleSpec spec = new DefaultAssembleSpec();
        spec.setTempDir(getTemporaryDir());

        spec.setObjectFileDir(getObjectFileDir());
        spec.source(getSource());
        spec.include(getIncludes());
        spec.args(getAssemblerArgs());
        spec.setOperationLogger(operationLogger);

        Compiler<AssembleSpec> compiler = toolChain.select(targetPlatform).newCompiler(AssembleSpec.class);
        WorkResult result = BuildOperationLoggingCompilerDecorator.wrap(compiler).execute(spec);
        setDidWork(result.getDidWork());
    }

    @InputFiles
    @SkipWhenEmpty
    public FileCollection getSource() {
        return source;
    }

    /**
     * Adds a set of assembler sources files to be translated. The provided sourceFiles object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void source(Object sourceFiles) {
        DefaultGroovyMethods.invokeMethod(source, "from", new Object[]{sourceFiles});
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
     * The tool chain being used to build.
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
    @InputFiles
    public FileCollection getIncludes() {
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
