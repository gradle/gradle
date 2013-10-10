package org.gradle.nativebinaries.language.c.internal.incremental;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FileState implements Serializable {
    private List<File> deps = new ArrayList<File>();
    private String text;

    public boolean isChanged(File now) {
        return text == null || !text.equals(getText(now));
    }

    private String getText(File now) {
        try {
            return DefaultGroovyMethods.getText(now);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<File> getDeps() {
        return deps;
    }
}
