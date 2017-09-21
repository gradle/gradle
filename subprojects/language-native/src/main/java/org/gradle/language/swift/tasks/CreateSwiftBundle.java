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
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileVar;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.language.swift.internal.SwiftStdlibToolLocator;
import org.gradle.process.ExecSpec;

import javax.inject.Inject;
import java.io.File;

/**
 * Creates Apple bundle from compiled Swift code.
 *
 * @since 4.3
 */
@Incubating
public class CreateSwiftBundle extends DefaultTask {
    private final RegularFileVar informationFile;
    private final RegularFileVar executableFile;
    private final RegularFileVar outputDir;
    private final SwiftStdlibToolLocator swiftStdlibToolLocator;

    @Inject
    public CreateSwiftBundle(SwiftStdlibToolLocator swiftStdlibToolLocator) {
        this.informationFile = newInputFile();
        this.executableFile = newInputFile();
        this.outputDir = newOutputFile();
        this.swiftStdlibToolLocator = swiftStdlibToolLocator;

    }

    @TaskAction
    void createBundle() {
        getProject().copy(new Action<CopySpec>() {
            @Override
            public void execute(CopySpec copySpec) {
                copySpec.from(getExecutableFile(), new Action<CopySpec>() {
                    @Override
                    public void execute(CopySpec copySpec) {
                        copySpec.into("Contents/MacOS");
                    }
                });

                copySpec.from(getInformationFile(), new Action<CopySpec>() {
                    @Override
                    public void execute(CopySpec copySpec) {
                        copySpec.into("Contents");
                    }
                });

                copySpec.into(getOutputDir());
            }
        });

        getProject().exec(new Action<ExecSpec>() {
            @Override
            public void execute(ExecSpec execSpec) {
                execSpec.executable(swiftStdlibToolLocator.find());
                File bundleDir = outputDir.getAsFile().get();
                execSpec.args(
                    "--copy",
                    "--scan-executable", executableFile.getAsFile().get().getAbsolutePath(),
                    "--destination", new File(bundleDir, "Contents/Frameworks").getAbsolutePath(),
                    "--platform", "macosx",
                    "--resource-destination", new File(bundleDir, "Contents/Resources").getAbsolutePath(),
                    "--scan-folder", new File(bundleDir, "Contents/Frameworks").getAbsolutePath()
                );
            }
        }).assertNormalExitValue();
    }

    @OutputDirectory
    public RegularFileVar getOutputDir() {
        return outputDir;
    }

    @InputFile
    public RegularFileVar getExecutableFile() {
        return executableFile;
    }

    @InputFile
    public RegularFileVar getInformationFile() {
        return informationFile;
    }
}
