/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.initialization.buildsrc;

import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.gradle.internal.scripts.ScriptFileUtil.getValidBuildFileNames;
import static org.gradle.internal.scripts.ScriptFileUtil.getValidSettingsFileNames;

public class BuildSrcDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildSrcDetector.class);
    private static final String[] GRADLE_BUILD_FILES;

    static {
        String[] settingsFileNames = getValidSettingsFileNames();
        String[] buildFileNames = getValidBuildFileNames();

        GRADLE_BUILD_FILES = new String[settingsFileNames.length + buildFileNames.length];
        System.arraycopy(settingsFileNames, 0, GRADLE_BUILD_FILES, 0, settingsFileNames.length);
        System.arraycopy(buildFileNames, 0, GRADLE_BUILD_FILES, settingsFileNames.length, buildFileNames.length);
    }

    public static boolean isValidBuildSrcBuild(File buildSrcDir) {
        if (!buildSrcDir.exists()) {
            return false;
        }
        if (!buildSrcDir.isDirectory()) {
            LOGGER.info("Ignoring buildSrc: not a directory.");
            return false;
        }

        for (String buildFileName : GRADLE_BUILD_FILES) {
            if (new File(buildSrcDir, buildFileName).exists()) {
                return true;
            }
        }
        if (containsFiles(new File(buildSrcDir, "src"))) {
            return true;
        }
        LOGGER.info("Ignoring buildSrc directory: does not contain 'settings.gradle[.kts]', 'build.gradle[.kts]', or a 'src' directory.");
        return false;
    }

    private static boolean containsFiles(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return false;
        }
        try (Stream<Path> directoryContents = Files.walk(directory.toPath())) {
            return directoryContents.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
