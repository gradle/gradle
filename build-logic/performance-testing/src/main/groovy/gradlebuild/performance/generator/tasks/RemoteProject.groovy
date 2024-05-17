/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.performance.generator.tasks

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

/**
 * Checkout a project template from a git repository.
 */
@CompileStatic
@DisableCachingByDefault(because = "Not worth caching")
abstract class RemoteProject extends DefaultTask {

    private final FileSystemOperations fsOps
    private final ExecOperations execOps

    @Inject
    RemoteProject(FileSystemOperations fsOps, ExecOperations execOps) {
        this.fsOps = fsOps
        this.execOps = execOps
    }

    /**
     * URI of the git repository.
     *
     * Either the remote git repository URL, the path to a local bare git repository or the path to a local git clone.
     */
    @Input
    abstract Property<String> getRemoteUri()

    /**
     * Git reference to use.
     */
    @Input
    abstract Property<String> getRef()

    /**
     * Relative path of a subdirectory within the git repository to use as the project template base directory.
     *
     * If unset, the root directory of the git repository is used.
     */
    @Input
    @Optional
    abstract Property<String> getSubdirectory()

    /**
     * Directory where the project template should be copied.
     */
    @OutputDirectory
    final File outputDirectory = project.layout.buildDirectory.dir("$name").get().asFile

    @TaskAction
    void checkoutAndCopy() {
        outputDirectory.deleteDir()
        File checkoutDir = checkout(fsOps, execOps, remoteUri.get(), ref.get(), temporaryDir)
        moveToOutputDir(checkoutDir, outputDirectory, subdirectory.getOrNull())
    }

    private static File cleanTemporaryDir(FileSystemOperations fsOps, File tmpDir) {
        if (tmpDir.exists()) {
            fsOps.delete {
                it.delete(tmpDir)
            }
        }
        return tmpDir
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    static File checkout(FileSystemOperations fsOps, ExecOperations execOps, String remoteUri, String ref, File checkoutDir) {
        cleanTemporaryDir(fsOps, checkoutDir)
        execOps.exec {
            commandLine = ["git", "clone", "--no-checkout", remoteUri, checkoutDir.absolutePath]
            errorOutput = System.out
        }
        execOps.exec {
            commandLine = ["git", "checkout", ref]
            workingDir = checkoutDir
            errorOutput = System.out
        }
        return checkoutDir
    }

    private static void moveToOutputDir(File tmpDir, File outputDirectory, String subdirectory) {
        File baseDir = subdirectory ? new File(tmpDir, subdirectory) : tmpDir
        baseDir.renameTo(outputDirectory)
    }
}
