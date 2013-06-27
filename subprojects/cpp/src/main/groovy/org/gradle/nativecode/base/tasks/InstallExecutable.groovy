/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.base.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.copy.FileCopyActionImpl
import org.gradle.api.internal.file.copy.FileCopySpecVisitor
import org.gradle.api.internal.file.copy.SyncCopySpecVisitor
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

public class InstallExecutable extends DefaultTask {
    InstallExecutable() {
        libs = project.files()
    }

    /**
     * The directory to install files into.
     */
    @OutputDirectory
    File destinationDir

    /**
     * The executable file to install.
     */
    @InputFile
    File executable

    @InputFiles
    FileCollection libs

    /**
     * Adds a set of library files to be linked.
     * The provided libs object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    void lib(Object libs) {
        this.libs.from libs
    }

    // TODO:DAZ Once we introduce a public type for OperatingSystem, make it configurable here
    private OperatingSystem os = OperatingSystem.current()

    @TaskAction
    void install() {
        if (os.windows) {
            installWindows()
        } else {
            installUnix()
        }
    }

    private void installWindows() {
        installToDir(getDestinationDir())
    }

    private void installUnix() {
        final destination = getDestinationDir()
        final executable = getExecutable()

        installToDir(new File(destination, "lib"))

        File script = new File(destination, executable.name);
        script.text = """
#/bin/sh
APP_BASE_NAME=`dirname "\$0"`
export DYLD_LIBRARY_PATH="\$APP_BASE_NAME/lib"
export LD_LIBRARY_PATH="\$APP_BASE_NAME/lib"
exec "\$APP_BASE_NAME/lib/${executable.name}" \"\$@\"
"""
        ant.chmod(perm: 'u+x', file: script)
    }

    private void installToDir(File binaryDir) {
        FileResolver fileResolver = getServices().get(FileResolver.class)

        def copyAction = new FileCopyActionImpl(fileResolver, new SyncCopySpecVisitor(new FileCopySpecVisitor()))

        def spec = copyAction.into(binaryDir)
        spec.from(getExecutable())
        spec.from(getLibs());
        copyAction.execute()
    }

}
