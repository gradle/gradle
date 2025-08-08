package org.example;

import org.gradle.api.file.RegularFileProperty;

public interface FileSizeDiffExtension {

    RegularFileProperty getFile1();

    RegularFileProperty getFile2();
}
