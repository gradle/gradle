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
import com.google.common.io.LineProcessor;
import org.gradle.api.Action;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.changedetection.changes.IncrementalTaskInputsInternal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.UncheckedException;
import org.gradle.process.ExecSpec;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class PreprocessNative extends SourceTask {
    private File preprocessedSourcesDir;

    private File gccExecutable;

    @OutputDirectory
    public File getPreprocessedSourcesDir() {
        return preprocessedSourcesDir;
    }

    public void setPreprocessedSourcesDir(File preprocessedSourcesDir) {
        this.preprocessedSourcesDir = preprocessedSourcesDir;
    }

    public File getGccExecutable() {
        return gccExecutable;
    }

    public void setGccExecutable(File gccExecutable) {
        this.gccExecutable = gccExecutable;
    }

    @TaskAction
    public void preprocess(IncrementalTaskInputs incrementalTaskInputs) {
        final File discoveredDependenciesDir = getTemporaryDir();
        final ConcurrentHashMap<String, Boolean> discoveredFiles = new ConcurrentHashMap<String, Boolean>();
        final IncrementalTaskInputsInternal inputs = (IncrementalTaskInputsInternal) incrementalTaskInputs;
        final File headersFile = getProject().file("src/main/headers");
        getSource().visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(final FileVisitDetails fileVisitDetails) {
                String name = fileVisitDetails.getName();
                if (!(name.endsWith(".cpp") || name.endsWith(".c"))) {
                    return;
                }
                String extension = name.endsWith(".cpp") ? "cpp" : "c";
                String base = name.replaceAll("\\.c(pp)?", "");
                String preprocessedName = base + (extension.equals("cpp") ? ".ii" : ".i");
                String depName = base + ".d";
                final File preprocessedFile = fileVisitDetails.getRelativePath().getParent().append(true, preprocessedName).getFile(getPreprocessedSourcesDir());
                final File depFile = fileVisitDetails.getRelativePath().getParent().append(true, depName).getFile(discoveredDependenciesDir);
                assert preprocessedFile.getParentFile().mkdirs();
                assert depFile.getParentFile().mkdirs();
                getProject().exec(new Action<ExecSpec>() {
                    @Override
                    public void execute(ExecSpec execSpec) {
                        execSpec.setExecutable(getGccExecutable());
                        execSpec.args("-E", "-MD", "-m64",
                            "-I", headersFile,
                            "-MF", depFile.getAbsolutePath(),
                            "-o", preprocessedFile.getAbsolutePath(),
                            fileVisitDetails.getFile().getAbsolutePath()
                        );
                    }
                });
                addDiscoveredInputs(inputs, depFile, discoveredFiles);
            }
        });
    }


    private void addDiscoveredInputs(final IncrementalTaskInputsInternal taskInputsInternal, File depFile, final ConcurrentHashMap<String, Boolean> discoveredFiles) {
        try {
            Files.readLines(depFile, Charsets.UTF_8, new LineProcessor<Void>() {
                @Override
                public boolean processLine(@Nonnull String line) throws IOException {
                    if (line.startsWith("  ")) {
                        String include = line.replaceAll(" \\\\$", "").trim();
                        if (discoveredFiles.putIfAbsent(include, Boolean.TRUE) == null) {
                            try {
                                File includedFile = new File(include).getAbsoluteFile().getCanonicalFile();
                                taskInputsInternal.newInput(includedFile);
                            } catch (IOException e) {
                                throw UncheckedException.throwAsUncheckedException(e);
                            }
                        }
                    }
                    return true;
                }

                @Override
                public Void getResult() {
                    return null;
                }
            });
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }


}
