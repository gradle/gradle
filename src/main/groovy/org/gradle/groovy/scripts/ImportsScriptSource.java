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

import org.gradle.api.internal.project.ImportsReader;
import org.gradle.util.GUtil;

import java.io.File;

public class ImportsScriptSource implements ScriptSource {
    private final ScriptSource source;
    private final ImportsReader importsReader;
    private final File rootDir;

    public ImportsScriptSource(ScriptSource source, ImportsReader importsReader, File rootDir) {
        this.source = source;
        this.importsReader = importsReader;
        this.rootDir = rootDir;
    }

    public ScriptSource getSource() {
        return source;
    }

    public String getText() {
        String text = source.getText();
        assert text != null;

        String imports;
        if (text.length() > 0) {
            imports = '\n' + importsReader.getImports(rootDir);
        } else {
            imports = "";
        }

        return text + imports;
    }

    public String getClassName() {
        return source.getClassName();
    }

    public File getSourceFile() {
        return source.getSourceFile();
    }

    public String getDisplayName() {
        return source.getDisplayName();
    }
}
