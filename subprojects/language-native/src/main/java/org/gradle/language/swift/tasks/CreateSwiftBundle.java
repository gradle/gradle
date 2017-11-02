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
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.swift.internal.SwiftStdlibToolLocator;
import org.gradle.process.ExecSpec;
import org.gradle.util.GFileUtils;

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

    @Inject
    protected FileSystem getFileSystem() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void createBundle() throws IOException {
        final File bundleDir = getOutputDir().dir("lib/" + getExecutableFile().getAsFile().get().getName() + ".xctest").get().getAsFile();
        getProject().copy(new Action<CopySpec>() {
            @Override
            public void execute(CopySpec copySpec) {
                copySpec.from(getExecutableFile(), new Action<CopySpec>() {
                    @Override
                    public void execute(CopySpec copySpec) {
                        copySpec.into("Contents/MacOS");
                    }
                });

                copySpec.into(bundleDir);
            }
        });

        File outputFile = new File(bundleDir, "Contents/Info.plist");
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
                execSpec.setWorkingDir(bundleDir);
                execSpec.executable(swiftStdlibToolLocator.find());
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

        installUnix();
    }

    private void installUnix() {
        final File destination = outputDir.getAsFile().get();
        final File executable = executableFile.getAsFile().get();

        //installToDir(new File(destination, "lib"));

        String runScriptText =
            "#!/bin/sh"
                + "\nAPP_BASE_NAME=`dirname \"$0\"`"
                + "\nXCTEST_LOCATION=`xcrun --find xctest`"
                + "\nexec \"$XCTEST_LOCATION\" \"$@\" \"$APP_BASE_NAME/lib/" + executable.getName() + ".xctest\""
                + "\n";
        GFileUtils.writeFile(runScriptText, getRunScript());

        getFileSystem().chmod(getRunScript(), 0755);
    }

    /**
     * Returns the script file that can be used to run the install image.
     *
     * @since 4.4
     */
    @Internal
    public File getRunScript() {
        return new File(outputDir.getAsFile().get(), OperatingSystem.current().getScriptName(executableFile.getAsFile().get().getName()));
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
