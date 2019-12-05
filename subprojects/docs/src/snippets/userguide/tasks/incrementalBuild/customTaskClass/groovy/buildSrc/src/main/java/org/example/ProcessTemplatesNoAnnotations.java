package org.example;

import java.io.File;
import java.util.HashMap;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.tasks.TaskAction;

public class ProcessTemplatesNoAnnotations extends DefaultTask {
    private TemplateEngineType templateEngine;
    private FileCollection sourceFiles;
    private TemplateData templateData;
    private File outputDir;

    public TemplateEngineType getTemplateEngine() {
        return this.templateEngine;
    }

    public FileCollection getSourceFiles() {
        return this.sourceFiles;
    }

    public TemplateData getTemplateData() {
        return this.templateData;
    }

    public File getOutputDir() { return this.outputDir; }

    public void setTemplateEngine(TemplateEngineType type) { this.templateEngine = type; }
    public void setSourceFiles(FileCollection files) { this.sourceFiles = files; }
    public void setTemplateData(TemplateData model) { this.templateData = model; }
    public void setOutputDir(File dir) { this.outputDir = dir; }

    @TaskAction
    public void processTemplates() {
        // ...
        getProject().copy(new Action<CopySpec>() {
            public void execute(CopySpec spec) {
                spec.into(outputDir).
                    from(sourceFiles).
                    expand(new HashMap<String, String>(templateData.getVariables()));
            }
        });
    }
}
