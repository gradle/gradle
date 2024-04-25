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

import org.gradle.scripts.ScriptingLanguage;

import java.util.List;

public class ScriptFileUtil {

    public static final String SETTINGS_FILE_BASE_NAME = "settings";

    public static final String BUILD_FILE_BASE_NAME = "build";

    public static String[] getValidSettingsFileNames() {
        return getFileNames(SETTINGS_FILE_BASE_NAME);
    }

    public static String[] getValidBuildFileNames() {
        return getFileNames(BUILD_FILE_BASE_NAME);
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
