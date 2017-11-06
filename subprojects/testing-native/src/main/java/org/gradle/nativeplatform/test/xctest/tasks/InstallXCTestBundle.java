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

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.util.GFileUtils;

import javax.inject.Inject;
import java.io.File;

public class InstallXCTestBundle extends DefaultTask {
    private final DirectoryProperty bundleDirectory = newInputDirectory();
    private final DirectoryProperty installDirectory = newOutputDirectory();

    @Inject
    protected FileSystem getFileSystem() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileOperations getFileOperations() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void install() {
        final File destination = installDirectory.getAsFile().get();
        final File bundle = bundleDirectory.getAsFile().get();

        installToDir(new File(destination, "lib"));

        String runScriptText =
            "#!/bin/sh"
                + "\nAPP_BASE_NAME=`dirname \"$0\"`"
                + "\nXCTEST_LOCATION=`xcrun --find xctest`"
                + "\nexec \"$XCTEST_LOCATION\" \"$@\" \"$APP_BASE_NAME/lib/" + bundle.getName() + "\""
                + "\n";
        GFileUtils.writeFile(runScriptText, getRunScript());

        getFileSystem().chmod(getRunScript(), 0755);
    }

    private void installToDir(final File binaryDir) {
        getFileOperations().sync(new Action<CopySpec>() {
            public void execute(CopySpec copySpec) {
                copySpec.into(binaryDir);
                copySpec.from(bundleDirectory.map(new Transformer<File, Directory>() {
                    @Override
                    public File transform(Directory directory) {
                        return directory.getAsFile().getParentFile();
                    }
                }));
            }
        });
    }

    /**
     * Returns the script file that can be used to run the install image.
     */
    @Internal
    public File getRunScript() {
        return new File(installDirectory.getAsFile().get(), FilenameUtils.removeExtension(bundleDirectory.getAsFile().get().getName()));
    }

    /**
     * Returns the bundle location property to install.
     */
    @SkipWhenEmpty
    @InputDirectory
    public DirectoryProperty getBundle() {
        return bundleDirectory;
    }

    /**
     * Returns the install directory property.
     */
    @OutputDirectory
    public DirectoryProperty getInstallDirectory() {
        return installDirectory;
    }
}
