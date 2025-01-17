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
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.nativeplatform.toolchain.internal.xcode.SwiftStdlibToolLocator;
import org.gradle.process.ExecOperations;
import org.gradle.util.internal.GFileUtils;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Creates a XCTest bundle with a run script so it can be easily executed.
 *
 * @since 4.4
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class InstallXCTestBundle extends DefaultTask {
    private final DirectoryProperty installDirectory;
    private final RegularFileProperty bundleBinaryFile;

    public InstallXCTestBundle() {
        ObjectFactory objectFactory = getProject().getObjects();
        installDirectory = objectFactory.directoryProperty();
        bundleBinaryFile = objectFactory.fileProperty();
        // A work around for not being able to skip the task when an input _file_ does not exist
        dependsOn(bundleBinaryFile);
    }

    @Inject
    protected SwiftStdlibToolLocator getSwiftStdlibToolLocator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileSystem getFileSystem() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileSystemOperations getFileSystemOperations() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    protected void install() throws IOException {
        File bundleFile = bundleBinaryFile.get().getAsFile();
        File bundleDir = installDirectory.get().file(bundleFile.getName() + ".xctest").getAsFile();
        installToDir(bundleDir, bundleFile);

        File runScript = getRunScriptFile().get().getAsFile();
        String runScriptText =
            "#!/bin/sh"
                + "\nAPP_BASE_NAME=`dirname \"$0\"`"
                + "\nXCTEST_LOCATION=`xcrun --find xctest`"
                + "\nexec \"$XCTEST_LOCATION\" \"$@\" \"$APP_BASE_NAME/" + bundleDir.getName() + "\""
                + "\n";

        GFileUtils.writeFile(runScriptText, runScript);
        getFileSystem().chmod(runScript, 0755);
    }

    private void installToDir(final File bundleDir, final File bundleFile) throws IOException {
        getFileSystemOperations().sync(SerializableLambdas.action(topSpec -> {
            topSpec.from(bundleFile, SerializableLambdas.action(spec -> spec.into("Contents/MacOS")));
            topSpec.into(bundleDir);
        }));

        File outputFile = new File(bundleDir, "Contents/Info.plist");

        Files.asCharSink(outputFile, Charset.forName("UTF-8")).write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
            + "<plist version=\"1.0\">\n"
            + "<dict/>\n"
            + "</plist>");

        getExecOperations().exec(SerializableLambdas.action(execSpec -> {
            execSpec.getWorkingDir().set(bundleDir);
            execSpec.executable(getSwiftStdlibToolLocator().find());
            execSpec.args(
                "--copy",
                "--scan-executable", bundleFile.getAbsolutePath(),
                "--destination", new File(bundleDir, "Contents/Frameworks").getAbsolutePath(),
                "--platform", "macosx",
                "--resource-destination", new File(bundleDir, "Contents/Resources").getAbsolutePath(),
                "--scan-folder", new File(bundleDir, "Contents/Frameworks").getAbsolutePath()
            );
        })).assertNormalExitValue();
    }

    /**
     * Returns the script file that can be used to run the install image.
     */
    @Internal
    public Provider<RegularFile> getRunScriptFile() {
        return installDirectory.file(bundleBinaryFile.getLocationOnly().map(SerializableLambdas.transformer(file -> FilenameUtils.removeExtension(file.getAsFile().getName()))));
    }

    /**
     * Returns the bundle binary file property.
     */
    @Internal("covered by getBundleBinary()")
    public RegularFileProperty getBundleBinaryFile() {
        return bundleBinaryFile;
    }

    @SkipWhenEmpty
    @Nullable
    @Optional
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputFile
    protected File getBundleBinary() {
        RegularFile bundle = getBundleBinaryFile().get();
        File bundleFile = bundle.getAsFile();
        if (!bundleFile.exists()) {
            return null;
        }
        return bundleFile;
    }

    /**
     * Returns the install directory property.
     */
    @OutputDirectory
    public DirectoryProperty getInstallDirectory() {
        return installDirectory;
    }

    @Inject
    protected abstract ExecOperations getExecOperations();
}
