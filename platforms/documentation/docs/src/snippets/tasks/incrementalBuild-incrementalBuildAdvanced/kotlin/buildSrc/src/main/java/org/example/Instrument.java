package org.example;

import java.io.File;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.*;

public abstract class Instrument extends DefaultTask {

    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    public abstract ConfigurableFileCollection getClassFiles();

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();

    @Inject
    protected abstract FileSystemOperations getFs();

    @TaskAction
    public void doIt() {
        getFs().copy(spec -> spec.
            into(getDestinationDir()).
            from(getClassFiles()).
            rename("(.*)\\.class", "$1_instrumented.class")
        );
    }
}
