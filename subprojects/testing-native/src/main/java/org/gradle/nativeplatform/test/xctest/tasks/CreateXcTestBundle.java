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

package org.gradle.nativeplatform.test.xctest.tasks;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileVar;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.nativeplatform.test.xctest.internal.SwiftStdlibToolLocator;
import org.gradle.process.ExecSpec;

import javax.inject.Inject;
import java.io.File;

/**
 * Creates XCTest bundle for execution.
 *
 * @since 4.2
 */
@Incubating
public class CreateXcTestBundle extends DefaultTask {
    private final RegularFileVar informationFile;
    private final RegularFileVar executableFile;
    private final DirectoryVar outputDir;
    private final SwiftStdlibToolLocator swiftStdlibToolLocator;

    @Inject
    public CreateXcTestBundle(SwiftStdlibToolLocator swiftStdlibToolLocator) {
        this.informationFile = newInputFile();
        this.executableFile = newInputFile();
        this.outputDir = newOutputDirectory();
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
                execSpec.args(
                    "--copy",
                    "--scan-executable", getExecutableFile().getAbsolutePath(),
                    "--destination", outputDir.dir("Contents/Frameworks").get().getAsFile().getAbsolutePath(),
                    "--platform", "macosx",
                    "--resource-destination", outputDir.dir("Contents/Resources").get().getAsFile().getAbsolutePath(),
                    "--scan-folder", outputDir.dir("Contents/Frameworks").get().getAsFile().getAbsolutePath()
                );
            }
        }).assertNormalExitValue();
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir.getAsFile().getOrNull();
    }

    public void setOutputDir(File outputDir) {
        this.outputDir.set(outputDir);
    }

    public void setOutputDir(Provider<? extends Directory> outputDir) {
        this.outputDir.set(outputDir);
    }

    @InputFile
    public File getExecutableFile() {
        return executableFile.getAsFile().getOrNull();
    }

    public void setExecutableFile(File executableFile) {
        this.executableFile.set(executableFile);
    }

    public void setExecutableFile(Provider<? extends RegularFile> executableFile) {
        this.executableFile.set(executableFile);
    }

    @InputFile
    public File getInformationFile() {
        return informationFile.getAsFile().getOrNull();
    }

    public void setInformationFile(File informationFile) {
        this.informationFile.set(informationFile);
    }

    public void setInformationFile(Provider<? extends RegularFile> informationFile) {
        this.informationFile.set(informationFile);
    }
}
