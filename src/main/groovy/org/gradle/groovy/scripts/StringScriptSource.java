package org.gradle.groovy.scripts;

import org.gradle.api.internal.project.ImportsReader;
import org.gradle.util.GUtil;

import java.io.File;

public class StringScriptSource implements ScriptSource {
    private final String description;
    private final String content;
    private final File rootDir;
    private final ImportsReader importsReader;

    public StringScriptSource(String description, String content, File rootDir, ImportsReader importsReader) {
        this.description = description;
        this.content = content;
        this.rootDir = rootDir;
        this.importsReader = importsReader;
    }

    public String getText() {
        if (!GUtil.isTrue(content)) {
            return null;
        }
        return importsReader.getImports(rootDir) + '\n' + content;
    }

    public String getClassName() {
        return "script";
    }

    public File getSourceFile() {
        return null;
    }

    public String getDescription() {
        return description;
    }
}
