/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.language.nativeplatform.tasks;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.swift.tasks.internal.SymbolHider;
import org.gradle.process.ExecSpec;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

import java.io.File;
import java.io.IOException;

/**
 * Unexports the <code>main</code> entry point symbol in an object file, so the object file can be linked with an executable.
 *
 * @since 4.4
 */
@CacheableTask
public abstract class UnexportMainSymbol extends DefaultTask {
    private final ConfigurableFileCollection source = getProject().files();
    private final DirectoryProperty outputDirectory = getProject().getObjects().directoryProperty();

    /**
     * The object files to relocate.
     *
     * @since 4.5
     */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public ConfigurableFileCollection getObjects() {
        return source;
    }

    /**
     * Collection of modified object files.
     *
     * @since 4.8
     */
    @Internal
    public FileCollection getRelocatedObjects() {
        return outputDirectory.getAsFileTree();
    }

    /**
     * Location of modified object files.
     *
     * @since 4.5
     */
    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    protected void unexport(InputChanges inputChanges) {
        for (FileChange change : inputChanges.getFileChanges(getObjects())) {
            if (change.getChangeType() == ChangeType.REMOVED) {
                File relocatedFileLocation = relocatedObject(change.getFile());
                relocatedFileLocation.delete();
            } else {
                if (change.getFile().isFile()) {
                    unexportMainSymbol(change.getFile());
                }
            }
        }
    }

    private void unexportMainSymbol(File object) {
        final File relocatedObject = relocatedObject(object);
        if (OperatingSystem.current().isWindows()) {
            try {
                final SymbolHider symbolHider = new SymbolHider(object);
                symbolHider.hideSymbol("main");     // 64 bit
                symbolHider.hideSymbol("_main");    // 32 bit
                symbolHider.hideSymbol("wmain");    // 64 bit
                symbolHider.hideSymbol("_wmain");   // 32 bit
                symbolHider.saveTo(relocatedObject);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } else {
            getProject().exec(new Action<ExecSpec>() {
                @Override
                public void execute(ExecSpec execSpec) {
                    // TODO: should use target platform to make this decision
                    if (OperatingSystem.current().isMacOsX()) {
                        execSpec.executable("ld"); // TODO: Locate this tool from a tool provider
                        execSpec.args(object);
                        execSpec.args("-o", relocatedObject);
                        execSpec.args("-r"); // relink, produce another object file
                        execSpec.args("-unexported_symbol", "_main"); // hide _main symbol
                    } else if (OperatingSystem.current().isLinux()) {
                        execSpec.executable("objcopy"); // TODO: Locate this tool from a tool provider
                        execSpec.args("-L", "main"); // hide main symbol
                        execSpec.args(object);
                        execSpec.args(relocatedObject);
                    } else {
                        throw new IllegalStateException("Do not know how to unexport a main symbol on " + OperatingSystem.current());
                    }
                }
            });
        }
    }

    private File relocatedObject(File object) {
        return outputDirectory.file(object.getName()).get().getAsFile();
    }
}
