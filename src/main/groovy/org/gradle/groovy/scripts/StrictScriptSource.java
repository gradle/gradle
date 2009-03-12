/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.GradleException;

import java.io.File;

public class StrictScriptSource implements ScriptSource {
    private final ScriptSource source;

    public StrictScriptSource(ScriptSource source) {
        this.source = source;
    }

    public ScriptSource getSource() {
        return source;
    }

    public String getText() {
        File sourceFile = source.getSourceFile();
        if (!sourceFile.exists()) {
            throw new GradleException(String.format("Cannot read %s as it does not exist.", source.getDescription()));
        }
        if (!sourceFile.isFile()) {
            throw new GradleException(String.format("Cannot read %s as it is not a file.", source.getDescription()));
        }
        return source.getText();
    }

    public String getClassName() {
        return source.getClassName();
    }

    public File getSourceFile() {
        return source.getSourceFile();
    }

    public String getDescription() {
        return source.getDescription();
    }
}
