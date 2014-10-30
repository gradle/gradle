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

import com.google.common.collect.Sets;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.daemon.InProcessCompilerDaemonFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.CleaningPlayToolCompiler;
import org.gradle.play.internal.routes.DaemonRoutesCompiler;
import org.gradle.play.internal.routes.RoutesCompileSpec;
import org.gradle.play.internal.routes.RoutesCompiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Task for compiling routes templates
 * */
public class RoutesCompile  extends SourceTask {

    /**
     * FileCollection presenting the twirl compiler classpath.
     */
    private FileCollection compilerClasspath;

    /**
     * Target directory for the compiled route files.
     */
    private File outputDirectory;

    /**
     * Additional imports used for by generated files.
     */
    private List<String> additionalImports = new ArrayList<String>();

    private String routesCompilerVersion;

    void setCompiler(Compiler<RoutesCompileSpec> compiler) {
        this.compiler = compiler;
    }

    private Compiler<RoutesCompileSpec> compiler;
    private RoutesStaleOutputCleaner cleaner;

    @InputFiles
    public FileCollection getCompilerClasspath() {
        return compilerClasspath;
    }

    public void setCompilerClasspath(FileCollection compilerClasspath) {
        this.compilerClasspath = compilerClasspath;
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
     * Specifies the additional imports of the Play Routes compiler.
     */
    public List<String> getAdditionalImports() {
        return additionalImports;
    }

    /**
     * Returns the additional imports of the Play Routes compiler.
     *
     * @return The additional imports.
     */
    public void setAdditionalImports(List<String> additionalImports) {
        this.additionalImports.addAll(additionalImports);
    }

    /**
     * Specifies the version for the Play Routes compiler.
     */
    public void setRoutesCompilerVersion(String routesCompilerVersion) {
        this.routesCompilerVersion = routesCompilerVersion;
    }

    /**
     * Returns the version used for the Play Routes compiler.
     *
     * @return The version of the Play Routes compiler.
     */
    public String getRoutesCompilerVersion() {
        return routesCompilerVersion;
    }


    @TaskAction
    void compile() {
        if(compiler==null){
            compiler = new CleaningPlayToolCompiler<RoutesCompileSpec>(getCompiler(), getOutputs());
        }
        RoutesCompileSpec spec = generateSpec(getSource().getFiles());
        compiler.execute(spec);
    }

    /**
     * For now just using InProcessCompilerDaemon.
     *
     * TODO allow forked compiler
     * */
    private Compiler<RoutesCompileSpec> getCompiler() {
        if (compiler == null) {
            ProjectInternal projectInternal = (ProjectInternal) getProject();
            InProcessCompilerDaemonFactory inProcessCompilerDaemonFactory = getServices().get(InProcessCompilerDaemonFactory.class);

            RoutesCompiler playRoutesCompiler = new RoutesCompiler(getRoutesCompilerVersion());
            compiler = new DaemonRoutesCompiler(projectInternal.getProjectDir(), playRoutesCompiler, inProcessCompilerDaemonFactory, getCompilerClasspath().getFiles());

        }
        return compiler;
    }

    private RoutesCompileSpec generateSpec(Set<File> files) {
        return new RoutesCompileSpec(files, getOutputDirectory(), getAdditionalImports());
    }

    void setCleaner(RoutesStaleOutputCleaner cleaner) {
        this.cleaner = cleaner;
    }

    private static class RoutesStaleOutputCleaner {
        private final File destinationDir;

        public RoutesStaleOutputCleaner(File destinationDir) {
            this.destinationDir = destinationDir;
        }

        public void execute(Set<File> staleSources) {
            for (File removedInputFile : staleSources) {
                Set<File> staleOutputFiles = calculateOutputFiles(removedInputFile);
                for (File staleOutputFile: staleOutputFiles) {
                    staleOutputFile.delete();
                }
            }
        }

        private Set<File> getRoutesFiles(File root) {
            return Sets.newHashSet(
                    new File(new File(root, "controllers"), "routes.java"),
                    new File(root, "routes_reverseRouting.scala"),
                    new File(root, "routes_routing.scala")
            );
        }

        Set<File> calculateOutputFiles(File inputFile) {
            String inputFileName = inputFile.getName();
            String[] splits = inputFileName.split("\\.");
            if (splits.length == 1 && splits[0].equals("routes")) {
                return getRoutesFiles(destinationDir);
            } else if (splits.length == 2 && splits[1].equals("routes")) {
                return getRoutesFiles(new File(destinationDir, splits[0]));
            } else {
                throw new IllegalArgumentException("Could not split " + inputFileName + " route compatible. Try to exclude this file (" + inputFile.getAbsolutePath()+") from Play routes.");
            }
        }
    }
}
