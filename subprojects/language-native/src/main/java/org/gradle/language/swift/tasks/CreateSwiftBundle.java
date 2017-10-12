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

import com.google.common.io.Files;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.language.swift.internal.SwiftStdlibToolLocator;
import org.gradle.process.ExecSpec;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Creates Apple bundle from compiled Swift code.
 *
 * @since 4.3
 */
@Incubating
public class CreateSwiftBundle extends DefaultTask {
    private final RegularFileProperty informationFile;
    private final RegularFileProperty executableFile;
    private final DirectoryProperty outputDir;
    private final SwiftStdlibToolLocator swiftStdlibToolLocator;

    @Inject
    public CreateSwiftBundle(SwiftStdlibToolLocator swiftStdlibToolLocator) {
        this.informationFile = newInputFile();
        this.executableFile = newInputFile();
        this.outputDir = newOutputDirectory();
        this.swiftStdlibToolLocator = swiftStdlibToolLocator;
    }

    @TaskAction
    void createBundle() throws IOException {
        getProject().copy(new Action<CopySpec>() {
            @Override
            public void execute(CopySpec copySpec) {
                copySpec.from(getExecutableFile(), new Action<CopySpec>() {
                    @Override
                    public void execute(CopySpec copySpec) {
                        copySpec.into("Contents/MacOS");
                    }
                });

                copySpec.into(getOutputDir());
            }
        });

        File outputFile = getOutputDir().file("Contents/Info.plist").get().getAsFile();
        if (!informationFile.isPresent() || !informationFile.get().getAsFile().exists()) {
            Files.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                + "<plist version=\"1.0\">\n"
                + "<dict/>\n"
                + "</plist>", outputFile, Charset.forName("UTF-8"));
        } else {
            Files.copy(informationFile.get().getAsFile(), outputFile);
        }

        getProject().exec(new Action<ExecSpec>() {
            @Override
            public void execute(ExecSpec execSpec) {
                execSpec.executable(swiftStdlibToolLocator.find());
                execSpec.args(
                    "--copy",
                    "--scan-executable", executableFile.getAsFile().get().getAbsolutePath(),
                    "--destination", outputDir.dir("Contents/Frameworks").get().getAsFile().getAbsolutePath(),
                    "--platform", "macosx",
                    "--resource-destination", outputDir.dir("Contents/Resources").get().getAsFile().getAbsolutePath(),
                    "--scan-folder", outputDir.dir("Contents/Frameworks").get().getAsFile().getAbsolutePath()
                );
            }
        }).assertNormalExitValue();
    }

    @OutputDirectory
    public DirectoryProperty getOutputDir() {
        return outputDir;
    }

    @InputFile
    public RegularFileProperty getExecutableFile() {
        return executableFile;
    }

    @Optional
    @InputFiles
    public RegularFileProperty getInformationFile() {
        return informationFile;
    }
}
