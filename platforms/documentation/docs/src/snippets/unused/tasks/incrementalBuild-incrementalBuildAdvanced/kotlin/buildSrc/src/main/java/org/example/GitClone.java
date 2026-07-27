/*
 * Copyright 2021 the original author or authors.
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

package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

// tag::git-clone[]
@UntrackedTask(because = "Git tracks the state") // <1>
public abstract class GitClone extends DefaultTask {

    @Input
    public abstract Property<String> getRemoteUri();

    @Input
    public abstract Property<String> getCommitId();

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();
// end::git-clone[]

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();
// tag::git-clone[]

    @TaskAction
    public void gitClone() throws IOException {
        File destinationDir = getDestinationDir().get().getAsFile().getAbsoluteFile(); // <2>
        String remoteUri = getRemoteUri().get();
        // Fetch origin or clone and checkout
        // ...
// end::git-clone[]
        if (isCorrectCheckout(destinationDir, remoteUri)) {
            getExecOperations().exec(spec -> {
                spec.commandLine("git", "fetch", "origin");
                spec.setWorkingDir(destinationDir);
            });
        } else {
            getFileSystemOperations().delete(spec -> {
                spec.delete(destinationDir);
            });
            if (!destinationDir.mkdirs()) {
                throw new IOException("Could not create directory " + destinationDir);
            }
            getExecOperations().exec(spec -> spec.commandLine("git", "clone", "--no-checkout", remoteUri, destinationDir.getAbsolutePath()));
        }
        getExecOperations().exec(spec -> {
            spec.commandLine("git", "checkout", getCommitId().get());
            spec.setWorkingDir(destinationDir);
        });
        getExecOperations().exec(spec -> {
            spec.commandLine("git", "clean", "-fdx");
            spec.setWorkingDir(destinationDir);
        });
// tag::git-clone[]
    }

    // end::git-clone[]
    private boolean isCorrectCheckout(File directory, String url) {
        if (!directory.isDirectory()) {
            return false;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExecResult result = getExecOperations().exec(spec -> {
            spec.commandLine("git", "remote", "get-url", "origin");
            spec.setIgnoreExitValue(true);
            spec.setStandardOutput(output);
            spec.setWorkingDir(directory);
        });
        String outputString = output.toString().trim();
        return result.getExitValue() == 0 && url.equals(outputString);
    }
// tag::git-clone[]
}
// end::git-clone[]
