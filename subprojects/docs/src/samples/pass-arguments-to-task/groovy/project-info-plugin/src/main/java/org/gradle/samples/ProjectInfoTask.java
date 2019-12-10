package org.gradle.samples;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import java.util.EnumSet;
import java.util.Collection;

public class ProjectInfoTask extends DefaultTask {

    enum Format {
        PLAIN, JSON
    }

    private Format format = Format.PLAIN;

    @Option(option = "format", description = "Output format of the project information.")
    public void setFormat(Format format) {
        this.format = format;
    }

    @OptionValues("format")
    public Collection<Format> getSupportedFormats() {
        return EnumSet.allOf(Format.class);
    }

    @TaskAction
    public void projectInfo() {
        switch (format) {
            case PLAIN:
                getLogger().lifecycle(getProject().getName() + ":" + getProject().getVersion());
                break;
            case JSON:
                getLogger().lifecycle("{\n" +
                    "    \"projectName\": \"" + getProject().getName() + "\"\n" +
                    "    \"version\": \"" + getProject().getVersion() + "\"\n}");
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

}
