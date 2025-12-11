package com.example;

import java.util.Collection;
import java.util.EnumSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;

class ProjectInfoTask extends DefaultTask {

    enum Format {
        PLAIN,
        JSON
    }

    private Format format = Format.PLAIN;

    private final String projectName = getProject().getName();
    private final String projectVersion = getProject().getVersion().toString();

    public ProjectInfoTask() {}

    @Option(option = "format", description = "Output format of the project information.")
    void setFormat(Format format) {
        this.format = format;
    }

    @OptionValues("format")
    Collection<Format> getSupportedFormats() {
        return EnumSet.allOf(Format.class);
    }

    @TaskAction
    void projectInfo() {
        switch (format) {
            case PLAIN:
                System.out.println(projectName + ":" + projectVersion);
                break;
            case JSON:
                System.out.println("{\n" + "    \"projectName\": \""
                        + projectName + "\"\n" + "    \"version\": \""
                        + projectVersion + "\"\n}");
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }
}
