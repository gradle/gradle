package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

public abstract class FileSizeDiffTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getFile1();

    @InputFile
    public abstract RegularFileProperty getFile2();

    @OutputFile
    public abstract RegularFileProperty getResultFile();

    @Inject
    public FileSizeDiffTask(ObjectFactory objects) {
        getResultFile().set(getProject().getLayout().getBuildDirectory().file("diff-result.txt"));
    }

    @TaskAction
    public void diff() throws IOException {
        File f1 = getFile1().getAsFile().get();
        File f2 = getFile2().getAsFile().get();

        String output;
        if (f1.length() == f2.length()) {
            output = "Files have the same size: " + f1.length() + " bytes";
        } else {
            String larger = f1.length() > f2.length() ? f1.getName() : f2.getName();
            long size = Math.max(f1.length(), f2.length());
            output = larger + " was larger: " + size + " bytes";
        }

        File result = getResultFile().get().getAsFile();
        result.getParentFile().mkdirs();
        writeText(result, output);

        System.out.println(output);
        System.out.println("Wrote diff result to " + result.getAbsolutePath());
    }

    private void writeText(File file, String content) throws IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
            writer.write(content);
        }
    }
}
