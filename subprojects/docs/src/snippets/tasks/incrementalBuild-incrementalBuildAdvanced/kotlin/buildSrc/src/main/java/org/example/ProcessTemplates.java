package org.example;

import java.util.HashMap;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

// tag::custom-task-class[]
public abstract class ProcessTemplates extends DefaultTask {
    // ...
// end::custom-task-class[]

    @Input
    public abstract Property<TemplateEngineType> getTemplateEngine();

// tag::custom-task-class[]
    @SkipWhenEmpty
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getSourceFiles();

    public void sources(FileCollection sourceFiles) {
        getSourceFiles().from(sourceFiles);
    }

    // ...
// end::custom-task-class[]

// tag::task-arg-method[]
    // ...
    public void sources(TaskProvider<?> inputTask) {
        getSourceFiles().from(getProject().getLayout().files(inputTask));
    }
    // ...
// end::task-arg-method[]

    @Nested
    public abstract TemplateData getTemplateData();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void processTemplates() {
        getProject().copy(spec -> spec.
            into(getOutputDir()).
            from(getSourceFiles()).
            expand(new HashMap<>(getTemplateData().getVariables().get()))
        );
    }
// tag::custom-task-class[]
}
// end::custom-task-class[]
