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

    public String getDisplayName() {
        return String.format("%s '%s'", description, sourceFile.getAbsolutePath());
    }
}
