/*
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.groovy.scripts;

import org.gradle.util.GFileUtils;
import org.gradle.util.HashUtil;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.InvalidUserDataException;

import java.io.File;

/**
 * A {@link ScriptSource} which loads the script from a file.
 */
public class FileScriptSource implements ScriptSource {
    private final String description;
    private final File sourceFile;

    public FileScriptSource(String description, File sourceFile) {
        this.description = description;
        this.sourceFile = sourceFile;
    }

    public String getText() {
        if (!sourceFile.exists()) {
            return "";
        }
        return GFileUtils.readFileToString(sourceFile);
    }

    /**
     * Returns the class name for use for this script source.  The
     * name is intended to be unique to support mapping class names
     * to source files even if many sources have the same file name
     * (e.g. build.gradle).
     */
    public String getClassName() {
        String name = sourceFile.getName();
        StringBuilder className = new StringBuilder(name.length());
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
        className.append('_');
        className.append(getId());

        return className.toString();
    }

    /**
     * Returns a (mostly) unique id for this file.
     */
    private String getId()
    {
        String path;
        try {
            path = GFileUtils.canonicalise(sourceFile).getAbsolutePath();
        } catch (UncheckedIOException e) {
            throw new InvalidUserDataException("Invalid source file '"+sourceFile+"'", e);
        }
        return HashUtil.createHash(path);
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public String getDebugInfo() {
        return GFileUtils.canonicalise(sourceFile).getPath();
    }

    public String getDisplayName() {
        return String.format("%s '%s'", description, sourceFile.getAbsolutePath());
    }
}
