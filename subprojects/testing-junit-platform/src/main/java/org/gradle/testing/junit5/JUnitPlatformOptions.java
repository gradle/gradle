package org.gradle.testing.junit5;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;

import java.io.Serializable;

public class JUnitPlatformOptions implements Serializable {
    private FileCollection classpathRoots;

    @InputFiles
    public FileCollection getClasspathRoots() {
        return classpathRoots;
    }

    public void setClasspathRoots(FileCollection classpathRoots) {
        this.classpathRoots = classpathRoots;
    }
}
