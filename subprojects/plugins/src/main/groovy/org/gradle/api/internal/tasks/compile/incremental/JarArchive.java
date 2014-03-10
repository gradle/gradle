package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;

public class JarArchive {
    final File file;
    final FileTree contents;
    public JarArchive(File jar, FileTree contents) {
        this.file = jar;
        this.contents = contents.matching(new PatternSet().include("**/*.class"));
    }
}
