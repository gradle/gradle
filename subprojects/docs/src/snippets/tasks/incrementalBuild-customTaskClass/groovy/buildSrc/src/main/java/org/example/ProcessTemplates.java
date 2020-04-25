// tag::custom-task-class[]
package org.example;

import java.io.File;
import java.util.HashMap;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.tasks.*;

public class ProcessTemplates extends DefaultTask {
    private TemplateEngineType templateEngine;
    private FileCollection sourceFiles;
    private TemplateData templateData;
    private File outputDir;

    @Input
    public TemplateEngineType getTemplateEngine() {
        return this.templateEngine;
    }

    @InputFiles
    public FileCollection getSourceFiles() {
        return this.sourceFiles;
    }

    @Nested
    public TemplateData getTemplateData() {
        return this.templateData;
    }

    @OutputDirectory
    public File getOutputDir() { return this.outputDir; }

    // + setter methods for the above - assume we’ve defined them

// end::custom-task-class[]
    public void setTemplateEngine(TemplateEngineType type) { this.templateEngine = type; }
    public void setSourceFiles(FileCollection files) { this.sourceFiles = files; }
    public void setTemplateData(TemplateData model) { this.templateData = model; }
    public void setOutputDir(File dir) { this.outputDir = dir; }

// tag::custom-task-class[]
    @TaskAction
    public void processTemplates() {
        // ...
// end::custom-task-class[]
        getProject().copy(new Action<CopySpec>() {
            public void execute(CopySpec spec) {
                spec.into(outputDir).
                    from(sourceFiles).
                    expand(new HashMap<String, String>(templateData.getVariables()));
            }
        });
// tag::custom-task-class[]
    }
}
// end::custom-task-class[]
