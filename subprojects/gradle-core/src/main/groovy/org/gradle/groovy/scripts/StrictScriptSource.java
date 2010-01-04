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

public class StrictScriptSource extends DelegatingScriptSource {

    public StrictScriptSource(ScriptSource source) {
        super(source);
    }

    public String getText() {
        ScriptSource source = getSource();
        File sourceFile = source.getSourceFile();

        if (sourceFile != null) {
            if (!sourceFile.exists()) {
                throw new GradleException(String.format("Cannot read %s as it does not exist.", source.getDisplayName()));
            }
            if (!sourceFile.isFile()) {
                throw new GradleException(String.format("Cannot read %s as it is not a file.", source.getDisplayName()));
            }
        }

        return source.getText();
    }
}
