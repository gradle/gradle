package org.gradle.nativebinaries.language.c.internal.incremental;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FileState implements Serializable {
    private List<File> deps = new ArrayList<File>();
    private byte[] hash;

    public byte[] getHash() {
        return hash;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    public List<File> getDeps() {
        return deps;
    }
}
