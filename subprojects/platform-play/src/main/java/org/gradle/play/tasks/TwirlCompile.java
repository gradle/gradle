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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.platform.base.internal.toolchain.ResolvedTool;
import org.gradle.play.internal.CleaningPlayToolCompiler;
import org.gradle.play.internal.twirl.DefaultTwirlCompileSpec;
import org.gradle.play.internal.twirl.TwirlCompileSpec;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Task for compiling twirl templates
 */
@Incubating
public class TwirlCompile extends SourceTask {

    /**
     * Target directory for the compiled template files.
     */
    private File outputDirectory;
    private BaseForkOptions forkOptions;
    private ResolvedTool<Compiler<TwirlCompileSpec>> compilerTool;
    private TwirlStaleOutputCleaner cleaner;
    private Object dependencyNotation;

    /**
     * fork options for the twirl compiler.
     */
    public BaseForkOptions getForkOptions() {
        if (forkOptions == null) {
            forkOptions = new BaseForkOptions();
        }
        return forkOptions;
    }

    public void setCompilerTool(ResolvedTool<Compiler<TwirlCompileSpec>> compilerTool) {
        this.compilerTool = compilerTool;
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

    @Input
    public Object getDependencyNotation() {
        return dependencyNotation;
    }

    public void setDependencyNotation(Object dependencyNotation) {
        this.dependencyNotation = dependencyNotation;
    }

    /**
     * Specifies the directory to generate the parser source files into.
     *
     * @param outputDirectory The output directory. Must not be null.
     */
    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @TaskAction
    void compile(IncrementalTaskInputs inputs) {
        RelativeFileCollector relativeFileCollector = new RelativeFileCollector();
        getSource().visit(relativeFileCollector);
        TwirlCompileSpec spec = new DefaultTwirlCompileSpec(relativeFileCollector.relativeFiles, getOutputDirectory(), getForkOptions(), useJavaDefaults());
        if (!inputs.isIncremental()) {
            new CleaningPlayToolCompiler<TwirlCompileSpec>(compilerTool.get(), getOutputs()).execute(spec);
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
            compilerTool.get().execute(spec);
        }
    }

    private boolean useJavaDefaults() {
        return false; //TODO: add this as a configurable parameter
    }

    void setCleaner(TwirlStaleOutputCleaner cleaner) {
        this.cleaner = cleaner;
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

    private static class RelativeFileCollector implements FileVisitor {
        List<RelativeFile> relativeFiles = Lists.newArrayList();

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
        }

        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            relativeFiles.add(new RelativeFile(fileDetails.getFile(), fileDetails.getRelativePath()));
        }
    }
}
