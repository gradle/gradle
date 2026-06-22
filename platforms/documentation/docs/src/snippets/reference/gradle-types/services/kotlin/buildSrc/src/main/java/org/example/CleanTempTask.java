package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

// tag::clean-temp-inject[]
public abstract class CleanTempTask extends DefaultTask {
    private final FileSystemOperations fs;

    @Inject
    public CleanTempTask(FileSystemOperations fs) {
        this.fs = fs;
    }

    @TaskAction
    public void run() {
        fs.delete(spec -> spec.delete("build/tmp"));
    }
}
// end::clean-temp-inject[]
