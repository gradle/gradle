/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.initialization.BuildLogicFiles;
import org.gradle.scripts.ScriptingLanguage;

import java.io.File;
import java.util.List;

public class ScriptFileUtil {

    public static File resolveBuildFile(File projectDir, ScriptFileResolver scriptFileResolver) {
        File buildScriptFile = scriptFileResolver.resolveScriptFile(projectDir, BuildLogicFiles.BUILD_FILE_BASENAME);
        if (buildScriptFile != null) {
            return buildScriptFile;
        }

        // If the build script file is not found, we assume it is the default file with empty content
        return new File(projectDir, BuildLogicFiles.DEFAULT_BUILD_FILE);
    }

    public static String[] getValidSettingsFileNames() {
        return getFileNames(BuildLogicFiles.SETTINGS_FILE_BASENAME);
    }

    public static String[] getValidBuildFileNames() {
        return getFileNames(BuildLogicFiles.BUILD_FILE_BASENAME);
    }

    public static String[] getValidExtensions() {
        List<ScriptingLanguage> scriptingLanguages = ScriptingLanguages.all();
        String[] extensions = new String[scriptingLanguages.size()];
        for (int i = 0; i < scriptingLanguages.size(); i++) {
            extensions[i] = scriptingLanguages.get(i).getExtension();
        }
        return extensions;
    }

    private static String[] getFileNames(String basename) {
        List<ScriptingLanguage> scriptingLanguages = ScriptingLanguages.all();
        String[] fileNames = new String[scriptingLanguages.size()];
        for (int i = 0; i < scriptingLanguages.size(); i++) {
            fileNames[i] = basename + scriptingLanguages.get(i).getExtension();
        }
        return fileNames;
    }
}
