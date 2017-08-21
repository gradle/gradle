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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.gradle.api.Action;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class CompileNative extends AbstractNativeTask {

    private final WorkerExecutor workerExecutor;
    private File outputDir;
    private File dependencyFile;

    @Inject
    public CompileNative(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    @Optional
    @InputFiles
    public Collection<String> getDependencies() throws IOException {
        File dependencyFile = getDependencyFile();
        return dependencyFile == null ? null :
            dependencyFile.isFile() ? Files.readLines(dependencyFile, Charsets.UTF_8) : null;
    }

    @Internal
    public File getDependencyFile() {
        return dependencyFile;
    }

    public void setDependencyFile(File dependencyFile) {
        this.dependencyFile = dependencyFile;
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
                if (!(name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".i") || name.endsWith(".ii"))) {
                    return;
                }
                String outputName = name.replaceAll("\\.i(i)?", ".o").replaceAll("\\.c(pp)?", ".o");
                final File outputFile = fileVisitDetails.getRelativePath().getParent().append(true, outputName).getFile(getOutputDir());
                assert outputFile.getParentFile().isDirectory() || outputFile.getParentFile().mkdirs();
                workerExecutor.submit(RunCxx.class, new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration workerConfiguration) {
                        workerConfiguration.setIsolationMode(IsolationMode.NONE);
                        workerConfiguration.setParams(
                            new File("."),
                            getGccExecutable().getAbsolutePath(),
                            args("-c",
                                "-o", outputFile.getAbsolutePath(),
                                fileVisitDetails.getFile().getAbsolutePath()));
                    }
                });
            }
        });

    }
}
