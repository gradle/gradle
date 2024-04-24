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

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScriptFileUtil {

    public static final String SETTINGS_FILE_BASE_NAME = "settings";

    public static final String BUILD_FILE_BASE_NAME = "build";

    public static Set<String> getValidSettingsFileNames() {
        return getFileNames(SETTINGS_FILE_BASE_NAME).collect(Collectors.toSet());
    }

    public static Set<String> getValidBuildFileNames() {
        return getFileNames(BUILD_FILE_BASE_NAME).collect(Collectors.toSet());
    }

    public static String[] getValidExtensions() {
        return getExtensions().toArray(String[]::new);
    }

    private static Stream<String> getExtensions() {
        return ScriptingLanguages.all().stream().map(ScriptingLanguage::getExtension);
    }

    private static Stream<String> getFileNames(String basename) {
        return getExtensions().map(extension -> basename + extension);
    }
}
