package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import java.util.EnumSet;
import java.util.Collection;

class ProjectInfoTask extends DefaultTask {

    enum Format {
        PLAIN, JSON
    }

    private Format format = Format.PLAIN;

    public ProjectInfoTask() {
    }

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
                System.out.println(getProject().getName() + ":" + getProject().getVersion());
                break;
            case JSON:
                System.out.println("{\n" +
                    "    \"projectName\": \"" + getProject().getName() + "\"\n" +
                    "    \"version\": \"" + getProject().getVersion() + "\"\n}");
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

}
