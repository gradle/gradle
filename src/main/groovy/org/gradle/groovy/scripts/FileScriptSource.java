package org.gradle.groovy.scripts;

import org.gradle.api.internal.project.ImportsReader;
import org.gradle.util.GFileUtils;

import java.io.File;

/**
 * A {@link ScriptSource} which loads the script from a file.
 */
public class FileScriptSource implements ScriptSource {
    private final String description;
    private final ImportsReader importsReader;
    private final File sourceFile;

    public FileScriptSource(String description, File sourceFile, ImportsReader importsReader) {
        this.description = description;
        this.sourceFile = sourceFile;
        this.importsReader = importsReader;
    }

    public String getText() {
        if (!sourceFile.exists()) {
            return null;
        }
        String imports = importsReader.getImports(sourceFile.getParentFile());
        String scriptContent = GFileUtils.readFileToString(sourceFile);
        return imports + '\n' + scriptContent;
    }

    public String getClassName() {
        String name = sourceFile.getName();
        StringBuilder className = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isJavaIdentifierPart(ch)) {
                className.append(ch);
            }
            else {
                className.append('_');
            }
        }
        if (!Character.isJavaIdentifierStart(className.charAt(0))) {
            className.insert(0, '_');
        }
        return className.toString();
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public String getDescription() {
        return String.format("%s '%s'", description, sourceFile.getAbsolutePath());
    }
}
