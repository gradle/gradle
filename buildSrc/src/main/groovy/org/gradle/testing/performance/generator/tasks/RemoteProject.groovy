/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.testing.performance.generator.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

class RemoteProject extends DefaultTask {
    @Input String remoteUri
    @Input @Optional String branch
    @Input @Optional Property<String> ref = project.objects.property(String)
    @Input @Optional String subdirectory
    @OutputDirectory File outputDirectory = project.file("$project.buildDir/$name")

    @TaskAction
    void checkout() {
        validateInputs()

        outputDirectory.deleteDir()
        File tmpDir = cleanTemporaryDir()

        if (branch) {
            checkoutBranch(remoteUri, branch, tmpDir)
        } else {
            checkoutRef(remoteUri, ref.get(), tmpDir)
        }

        moveToOutputDir(tmpDir, outputDirectory, subdirectory)
    }

    private void validateInputs() {
        if (!branch && !ref.isPresent()) {
            throw new InvalidUserDataException("Either ${name}.branch or ${name}.ref must be set")
        }
        if (branch && ref.isPresent() && branch != ref.get()) {
            throw new InvalidUserDataException(
                "Both ${name}.branch and ${name}.ref cannot have different values, " +
                    "respectively '$branch' and '${ref.get()}'")
        }
    }

    private File cleanTemporaryDir() {
        File tmpDir = getTemporaryDir()
        if (tmpDir.exists()) {
            project.delete(tmpDir)
        }
        return tmpDir
    }

    private void checkoutBranch(String remoteUri, String branch, File tmpDir) {
        project.exec {
            commandLine = ["git", "clone", "--depth", "1", "--branch", branch, remoteUri, tmpDir.absolutePath]
            if (OperatingSystem.current().windows) {
                commandLine = ["cmd", "/c"] + commandLine
            }
            errorOutput = System.out
        }
    }

    private void checkoutRef(String remoteUri, String ref, File tmpDir) {
        project.exec {
            commandLine = ["git", "clone", remoteUri, tmpDir.absolutePath]
            errorOutput = System.out
        }
        project.exec {
            commandLine = ["git", "checkout", ref]
            workingDir = tmpDir
            errorOutput = System.out
        }
    }

    private static void moveToOutputDir(File tmpDir, File outputDirectory, String subdirectory) {
        File baseDir = subdirectory ? new File(tmpDir, subdirectory) : tmpDir
        baseDir.renameTo(outputDirectory)
    }
}
