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

package org.gradle.language.cacheable;

import org.gradle.api.Action;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CompileNative extends SourceTask {

    private File gccExecutable;
    private File outputDir;

    public List<String> getCompilerOptions() {
        return compilerOptions;
    }

    private List<String> compilerOptions = new ArrayList<String>();

    @Input
    public File getGccExecutable() {
        return gccExecutable;
    }

    public void setGccExecutable(File gccExecutable) {
        this.gccExecutable = gccExecutable;
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    @TaskAction
    public void compile() {
        getSource().visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(final FileVisitDetails fileVisitDetails) {
                String name = fileVisitDetails.getName();
                String outputName = name.replaceAll("\\.i(i)?", ".o");
                final File outputFile = fileVisitDetails.getRelativePath().getParent().append(true, outputName).getFile(getOutputDir());
                assert outputFile.getParentFile().mkdirs();
                getProject().exec(new Action<ExecSpec>() {
                    @Override
                    public void execute(ExecSpec execSpec) {
                        execSpec.setExecutable(getGccExecutable());
                        execSpec.args("-m64", "-c",
                            "-o", outputFile.getAbsolutePath(),
                            fileVisitDetails.getFile().getAbsolutePath()
                        );
                        execSpec.args(compilerOptions);
                    }
                });
            }
        });

    }

}
