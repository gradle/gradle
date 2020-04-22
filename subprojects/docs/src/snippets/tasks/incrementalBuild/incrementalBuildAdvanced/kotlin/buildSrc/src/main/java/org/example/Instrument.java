package org.example;

import java.io.File;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.tasks.*;

public class Instrument extends DefaultTask {
    private FileCollection classFiles;
    private File destinationDir;

    @SkipWhenEmpty
    @InputFiles
    public FileCollection getClassFiles() {
        return this.classFiles;
    }

    public void setClassFiles(FileCollection files) {
        this.classFiles = files;
    }

    @OutputDirectory
    public File getDestinationDir() { return this.destinationDir; }
    public void setDestinationDir(File dir) { this.destinationDir = dir; }

    @TaskAction
    public void doIt() {
        getProject().copy(new Action<CopySpec>() {
            public void execute(CopySpec spec) {
                spec.into(destinationDir).
                    from(classFiles).
                    rename("(.*)\\.class", "$1_instrumented.class");
            }
        });
    }
}
