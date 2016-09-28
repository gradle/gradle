package org.example;

import java.io.File;
import java.util.HashMap;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.tasks.*;

// START SNIPPET custom-task-class
public class ProcessTemplates extends DefaultTask {
    // ...
// END SNIPPET custom-task-class
    private TemplateEngineType templateEngine;
    private TemplateData templateData;
    private File outputDir;

    @Input
    public TemplateEngineType getTemplateEngine() {
        return this.templateEngine;
    }
// START SNIPPET custom-task-class
    private FileCollection sourceFiles = getProject().files();

    @SkipWhenEmpty
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public FileCollection getSourceFiles() {
        return this.sourceFiles;
    }

    public void sources(FileCollection sourceFiles) {
        this.sourceFiles = this.sourceFiles.plus(sourceFiles);
    }

    // ...
// END SNIPPET custom-task-class

// START SNIPPET task-arg-method
    // ...
    public void sources(Task inputTask) {
        this.sourceFiles = this.sourceFiles.plus(getProject().files(inputTask));
    }
    // ...
// END SNIPPET task-arg-method

    @Nested
    public TemplateData getTemplateData() {
        return this.templateData;
    }

    @OutputDirectory
    public File getOutputDir() { return this.outputDir; }

    // + setter methods for the above - assume we’ve defined them

    public void setTemplateEngine(TemplateEngineType type) { this.templateEngine = type; }
    public void setSourceFiles(FileCollection files) { this.sourceFiles = files; }
    public void setTemplateData(TemplateData model) { this.templateData = model; }
    public void setOutputDir(File dir) { this.outputDir = dir; }

    @TaskAction
    public void processTemplates() {
        getProject().copy(new Action<CopySpec>() {
            public void execute(CopySpec spec) {
                spec.into(outputDir).
                    from(sourceFiles).
                    expand(new HashMap<String, String>(templateData.getVariables()));
            }
        });
    }
// START SNIPPET custom-task-class
}
// END SNIPPET custom-task-class
