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

package org.gradle.play.tasks;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.play.internal.CleaningPlayToolCompiler;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.internal.twirl.DefaultTwirlCompileSpec;
import org.gradle.play.internal.twirl.TwirlCompileSpec;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.toolchain.PlayToolChain;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Task for compiling twirl templates
 */
public class TwirlCompile extends SourceTask {

    /**
     * Target directory for the compiled template files.
     */
    private File outputDirectory;

    /**
     * Source directory for the template files. Used to find the relative path of templates.
     */
    private File sourceDirectory;

    private BaseForkOptions forkOptions;

    private Compiler<TwirlCompileSpec> compiler;

    private TwirlStaleOutputCleaner cleaner;
    private PlayPlatform platform;

    /**
     * fork options for the twirl compiler.
     */
    public BaseForkOptions getForkOptions() {
        if (forkOptions == null) {
            forkOptions = new BaseForkOptions();
        }
        return forkOptions;
    }

    void setCompiler(Compiler<TwirlCompileSpec> compiler) {
        this.compiler = compiler;
    }

    /**
     * Returns the directory to generate the parser source files into.
     *
     * @return The output directory.
     */
    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Specifies the directory to generate the parser source files into.
     *
     * @param outputDirectory The output directory. Must not be null.
     */
    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Returns the root directory where sources are found. Used to find the relative path of templates.
     *
     * @return The root directory for sources.
     */
    public File getSourceDirectory() {
        return sourceDirectory;
    }

    /**
     * Specifies the root directory where sources are found. Used to find the relative path of templates.
     *
     * @param sourceDirectory TThe root directory for sources.
     */
    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
        this.setSource(sourceDirectory);
    }

    @TaskAction
    void compile(IncrementalTaskInputs inputs) {
        TwirlCompileSpec spec = new DefaultTwirlCompileSpec(getSourceDirectory(), getSource().getFiles(), getOutputDirectory(), getForkOptions(), useJavaDefaults());
        if (!inputs.isIncremental()) {
            if (compiler == null) {
                compiler = new CleaningPlayToolCompiler<TwirlCompileSpec>(getCompiler(spec), getOutputs());
            }
            compiler.execute(spec);
        } else {
            final Set<File> sourcesToCompile = new HashSet<File>();
            inputs.outOfDate(new Action<InputFileDetails>() {
                public void execute(InputFileDetails inputFileDetails) {
                    sourcesToCompile.add(inputFileDetails.getFile());
                }
            });

            final Set<File> staleOutputFiles = new HashSet<File>();
            inputs.removed(new Action<InputFileDetails>() {
                public void execute(InputFileDetails inputFileDetails) {
                    staleOutputFiles.add(inputFileDetails.getFile());
                }
            });
            if (cleaner == null) {
                cleaner = new TwirlStaleOutputCleaner(getOutputDirectory());
            }
            cleaner.execute(staleOutputFiles);
            getCompiler(spec).execute(spec);
        }
    }

    private Compiler<TwirlCompileSpec> getCompiler(TwirlCompileSpec spec) {
        if (compiler == null) {
            ToolProvider select = ((PlayToolChainInternal) getToolChain()).select(platform);
            compiler = select.newCompiler(spec);
        }
        return compiler;
    }

    private boolean useJavaDefaults() {
        return false; //TODO: add this as a configurable parameter
    }

    void setCleaner(TwirlStaleOutputCleaner cleaner) {
        this.cleaner = cleaner;
    }

    public void setPlatform(PlayPlatform platform) {
        this.platform = platform;
    }

    /**
     * Returns the tool chain that will be used to compile the twirl source.
     *
     * @return The tool chain.
     */
    @Incubating
    @Inject
    public PlayToolChain getToolChain() {
        // Implementation is generated
        throw new UnsupportedOperationException();
    }

    private static class TwirlStaleOutputCleaner {
        private final File destinationDir;

        public TwirlStaleOutputCleaner(File destinationDir) {
            this.destinationDir = destinationDir;
        }

        public void execute(Set<File> staleSources) {
            for (File removedInputFile : staleSources) {
                File staleOuputFile = calculateOutputFile(removedInputFile);
                staleOuputFile.delete();
            }
        }

        File calculateOutputFile(File inputFile) {
            String inputFileName = inputFile.getName();
            String[] splits = inputFileName.split("\\.");
            String relativeOutputFilePath = String.format("views/%s/%s.template.scala", splits[2], splits[0]); //TODO: use Twirl library instead?
            return new File(destinationDir, relativeOutputFilePath);
        }
    }
}
