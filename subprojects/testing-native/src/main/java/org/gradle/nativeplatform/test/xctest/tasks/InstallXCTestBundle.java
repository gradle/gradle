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

import com.google.common.io.Files;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Transformer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.language.swift.internal.SwiftStdlibToolLocator;
import org.gradle.process.ExecSpec;
import org.gradle.util.GFileUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;

/**
 * Creates a XCTest bundle with a run script so it can be easily executed.
 *
 * @since 4.4
 */
@Incubating
public class InstallXCTestBundle extends DefaultTask {
    private final DirectoryProperty installDirectory = newOutputDirectory();
    private final RegularFileProperty bundleBinaryFile = newInputFile();

    @Inject
    protected SwiftStdlibToolLocator getSwiftStdlibToolLocator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileSystem getFileSystem() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileOperations getFileOperations() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void install() throws IOException {
        final Directory destination = installDirectory.get();
        final Directory bundle = getBundleDirectory().get();

        installToDir(bundle);

        String runScriptText =
            "#!/bin/sh"
                + "\nAPP_BASE_NAME=`dirname \"$0\"`"
                + "\nXCTEST_LOCATION=`xcrun --find xctest`"
                + "\nexec \"$XCTEST_LOCATION\" \"$@\" \"$APP_BASE_NAME/" + bundle.getAsFile().getName() + "\""
                + "\n";
        GFileUtils.writeFile(runScriptText, getRunScriptFile().get().getAsFile());

        getFileSystem().chmod(getRunScriptFile().get().getAsFile(), 0755);
    }

    private void installToDir(final Directory bundleDir) throws IOException {
        getFileOperations().sync(new Action<CopySpec>() {
            @Override
            public void execute(CopySpec copySpec) {
                copySpec.from(getBundleBinaryFile(), new Action<CopySpec>() {
                    @Override
                    public void execute(CopySpec copySpec) {
                        copySpec.into("Contents/MacOS");
                    }
                });

                copySpec.into(bundleDir);
            }
        });

        RegularFile outputFile = bundleDir.file("Contents/Info.plist");
        outputFile.getAsFile().getParentFile().mkdirs();
        Files.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
            + "<plist version=\"1.0\">\n"
            + "<dict/>\n"
            + "</plist>", outputFile.getAsFile(), Charset.forName("UTF-8"));

        getProject().exec(new Action<ExecSpec>() {
            @Override
            public void execute(ExecSpec execSpec) {
                execSpec.setWorkingDir(bundleDir.getAsFile());
                execSpec.executable(getSwiftStdlibToolLocator().find());
                execSpec.args(
                    "--copy",
                    "--scan-executable", bundleBinaryFile.get().getAsFile().getAbsolutePath(),
                    "--destination", bundleDir.dir("Contents/Frameworks").getAsFile().getAbsolutePath(),
                    "--platform", "macosx",
                    "--resource-destination", bundleDir.dir("Contents/Resources").getAsFile().getAbsolutePath(),
                    "--scan-folder", bundleDir.dir("Contents/Frameworks").getAsFile().getAbsolutePath()
                );
            }
        }).assertNormalExitValue();
    }

    /**
     * Returns the script file that can be used to run the install image.
     */
    @Internal
    public Provider<RegularFile> getRunScriptFile() {
        return installDirectory.file(getProject().provider(new Callable<CharSequence>() {
            @Override
            public CharSequence call() throws Exception {
                return FilenameUtils.removeExtension(bundleBinaryFile.get().getAsFile().getName());
            }
        }));
    }

    @Internal
    public Provider<Directory> getBundleDirectory() {
        return installDirectory.dir(bundleBinaryFile.map(new Transformer<String, RegularFile>() {
            @Override
            public String transform(RegularFile regularFile) {
                return regularFile.getAsFile().getName() + ".xctest";
            }
        }));
    }

    /**
     * Returns the bundle binary file property.
     */
    @Internal("Covered by inputFileIfExists")
    public RegularFileProperty getBundleBinaryFile() {
        return bundleBinaryFile;
    }

    /**
     * Returns the install directory property.
     */
    @OutputDirectory
    public DirectoryProperty getInstallDirectory() {
        return installDirectory;
    }

    /**
     * Workaround for when the task is given an input file that doesn't exist
     */
    @SkipWhenEmpty
    @Optional
    @InputFile
    protected File getInputFileIfExists() {
        File inputFile = bundleBinaryFile.get().getAsFile();
        if (inputFile != null && inputFile.exists()) {
            return inputFile;
        } else {
            return null;
        }
    }
}
