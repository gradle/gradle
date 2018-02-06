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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.gradle.internal.FileUtils.hasExtension;

public class DefaultScriptFileResolver implements ScriptFileResolver {

    @Override
    public File resolveScriptFile(File dir, String basename) {
        for (String extension : ScriptingLanguages.EXTENSIONS) {
            File scriptFile = new File(dir, basename + extension);
            if (scriptFile.isFile()) {
                return scriptFile;
            }
        }
        return null;
    }

    @Override
    public List<File> findScriptsIn(File dir) {
        File[] candidates = dir.listFiles();
        if (candidates == null || candidates.length == 0) {
            return emptyList();
        }
        List<File> files = new ArrayList<File>(candidates.length);
        for (File file : candidates) {
            if (file.isFile() && hasScriptExtension(file)) {
                files.add(file);
            }
        }
        return files;
    }

    private boolean hasScriptExtension(File file) {
        for (String extension : ScriptingLanguages.EXTENSIONS) {
            if (hasExtension(file, extension)) {
                return true;
            }
        }
        return false;
    }
}
