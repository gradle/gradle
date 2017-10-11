/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.scripts;

import org.gradle.scripts.ScriptingLanguage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultScriptFileResolver implements ScriptFileResolver {

    public static ScriptFileResolver empty() {
        return new DefaultScriptFileResolver(Collections.<String>emptyList());
    }

    public static ScriptFileResolver forDefaultScriptingLanguages() {
        return forScriptingLanguages(DefaultScriptingLanguages.create());
    }

    public static ScriptFileResolver forScriptingLanguages(Iterable<ScriptingLanguage> scriptingLanguages) {
        List<String> extensions = new ArrayList<String>();
        for (ScriptingLanguage scriptingLanguage : scriptingLanguages) {
            extensions.add(scriptingLanguage.getExtension());
        }
        return new DefaultScriptFileResolver(extensions);
    }

    private final Iterable<String> scriptingExtensions;

    private DefaultScriptFileResolver(Iterable<String> scriptingExtensions) {
        this.scriptingExtensions = scriptingExtensions;
    }

    @Override
    public File resolveScriptFile(File dir, String basename) {
        for (String extension : scriptingExtensions) {
            File scriptFile = new File(dir, basename + extension);
            if (scriptFile.isFile()) {
                return scriptFile;
            }
        }
        return null;
    }
}
