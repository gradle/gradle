package org.example;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.file.RegularFileProperty;

import javax.inject.Inject;

public class FileSizeDiffExtension {

    private final RegularFileProperty file1;
    private final RegularFileProperty file2;

    @Inject
    public FileSizeDiffExtension(ObjectFactory objects) {
        this.file1 = objects.fileProperty();
        this.file2 = objects.fileProperty();
    }

    public RegularFileProperty getFile1() {
        return file1;
    }

    public RegularFileProperty getFile2() {
        return file2;
    }
}
