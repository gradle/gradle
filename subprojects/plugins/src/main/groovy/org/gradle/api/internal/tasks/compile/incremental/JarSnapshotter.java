package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.hash.Hasher;

import java.util.HashMap;
import java.util.Map;

public class JarSnapshotter {

    private final Hasher hasher;

    public JarSnapshotter(Hasher hasher) {
        this.hasher = hasher;
    }

    JarSnapshot createSnapshot(FileTree archivedClasses) {
        final Map<String, byte[]> hashes = new HashMap<String, byte[]>();
        archivedClasses.visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {
            }

            public void visitFile(FileVisitDetails fileDetails) {
                hashes.put(fileDetails.getPath().replaceAll("/", ".").replaceAll("\\.class$", ""), hasher.hash(fileDetails.getFile()));
            }
        });
        return new JarSnapshot(hashes);
    }
}
