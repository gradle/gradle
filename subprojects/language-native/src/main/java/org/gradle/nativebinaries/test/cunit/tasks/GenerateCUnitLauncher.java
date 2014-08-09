package org.gradle.nativebinaries.test.cunit.tasks;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Generated the Gradle CUnit launcher: main method and header.
 */
public class GenerateCUnitLauncher extends DefaultTask {
    private File sourceDir;
    private File headerDir;

    @TaskAction
    public void generate() {
        writeToFile(sourceDir, "gradle_cunit_main.c");
        writeToFile(headerDir, "gradle_cunit_register.h");
    }

    private void writeToFile(File directory, String fileName) {
        final File file = new File(directory, fileName);
        try {
            IOUtils.copy(getClass().getResourceAsStream(fileName), new FileWriter(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @OutputDirectory
    public File getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(File sourceDir) {
        this.sourceDir = sourceDir;
    }

    @OutputDirectory
    public File getHeaderDir() {
        return headerDir;
    }

    public void setHeaderDir(File headerDir) {
        this.headerDir = headerDir;
    }
}
