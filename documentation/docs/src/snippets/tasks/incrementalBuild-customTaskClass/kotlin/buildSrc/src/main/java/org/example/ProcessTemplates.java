// tag::custom-task-class[]
package org.example;

import java.util.HashMap;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

public abstract class ProcessTemplates extends DefaultTask {

    @Input
    public abstract Property<TemplateEngineType> getTemplateEngine();

    @InputFiles
    public abstract ConfigurableFileCollection getSourceFiles();

    @Nested
    public abstract TemplateData getTemplateData();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void processTemplates() {
        // ...
// end::custom-task-class[]
        getProject().copy(spec -> spec.
            into(getOutputDir()).
            from(getSourceFiles()).
            expand(new HashMap<>(getTemplateData().getVariables().get()))
        );
// tag::custom-task-class[]
    }
}
// end::custom-task-class[]
