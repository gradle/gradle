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

package org.gradle.language.swift.tasks;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.ExecSpec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Set;

/**
 * Relocates the symbol <code>_main</code> to <code>relocated_main</code> in an object file, so the
 *
 * @since 4.4
 */
@Incubating
public class RelocateMainSymbol extends SourceTask {
    private File mainObjectFile;
    private final DirectoryProperty outputFile = newOutputDirectory();

    public RelocateMainSymbol() {
        outputFile.set(getTemporaryDir());
    }

    @OutputFiles
    public FileCollection getOutputFiles() {
        return outputFile.getAsFileTree();
    }

    @InputFile
    @Optional
    public File getMainObject() {
        if (mainObjectFile == null) {
            mainObjectFile = findMainObject();
        }
        return mainObjectFile;
    }

    @TaskAction
    public void relocate() {
        final File mainObjectFile = getMainObject();
        if (mainObjectFile != null) {
            final File relocatedMainObject = outputFile.file(mainObjectFile.getName()).get().getAsFile();
            getProject().exec(new Action<ExecSpec>() {
                @Override
                public void execute(ExecSpec execSpec) {
                    if (OperatingSystem.current().isMacOsX()) {
                        execSpec.executable("ld"); // TODO: Locate this tool from a tool provider
                        execSpec.args(mainObjectFile);
                        execSpec.args("-o", relocatedMainObject);
                        execSpec.args("-r"); // relink, produce another object file
                        execSpec.args("-unexported_symbol", "_main"); // hide _main symbol
                    } else if (OperatingSystem.current().isLinux()) {
                        execSpec.executable("objcopy"); // TODO: Locate this tool from a tool provider
                        execSpec.args("-L", "main"); // hide _main symbol
                        execSpec.args(mainObjectFile);
                        execSpec.args(relocatedMainObject);
                    } else {
                        throw new IllegalStateException("Do not know how to relocate a main symbol on " + OperatingSystem.current());
                    }
                }
            });
            setDidWork(true);
        } else {
            setDidWork(getProject().delete(outputFile.get().getAsFile()));
        }
    }

    private File findMainObject() {
        Set<File> objectFiles = getSource().getFiles();
        if (objectFiles.isEmpty()) {
            return null;
        } else if (objectFiles.size() == 1) {
            return objectFiles.iterator().next();
        } else {
            return CollectionUtils.findFirst(objectFiles, new Spec<File>() {
                @Override
                public boolean isSatisfiedBy(File objectFile) {
                    return objectFile.getName().equalsIgnoreCase("main.o");
                }
            });
        }
    }
}
