/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.internal.SystemProperties;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.internal.os.OperatingSystem;
import org.jspecify.annotations.Nullable;

import java.io.File;

import static org.gradle.internal.FileUtils.canonicalize;

/**
 * Mutable build layout parameters
 */
public class BuildLayoutParameters {
    private static final File DEFAULT_GRADLE_USER_HOME = new File(SystemProperties.getInstance().getUserHome() + "/.gradle");
    public static final String GRADLE_USER_HOME_PROPERTY_KEY = "gradle.user.home";
    private static final String GRADLE_USER_HOME_ENV_KEY = "GRADLE_USER_HOME";

    private @Nullable File gradleInstallationHomeDir;
    private File gradleUserHomeDir;
    private @Nullable File projectDir;
    private File currentDir;

    public BuildLayoutParameters() {
        this(
            findGradleInstallationHomeDir(),
            findGradleUserHomeDir(),
            null,
            canonicalize(SystemProperties.getInstance().getCurrentDir())
        );
    }

    public BuildLayoutParameters(
        @Nullable File gradleInstallationHomeDir,
        File gradleUserHomeDir,
        @Nullable File projectDir,
        File currentDir
    ) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.gradleInstallationHomeDir = gradleInstallationHomeDir;
        this.projectDir = projectDir;
        this.currentDir = currentDir;
    }

    static private File findGradleUserHomeDir() {
        String gradleUserHome = System.getProperty(GRADLE_USER_HOME_PROPERTY_KEY);
        if (gradleUserHome == null) {
            gradleUserHome = System.getenv(GRADLE_USER_HOME_ENV_KEY);
        }
        if (gradleUserHome == null) {
            File osDataDirectory = osDataDirectory();
            if (osDataDirectory != null) {
                File osDataCandidate = new File(osDataDirectory, "Gradle");
                if (osDataCandidate.isDirectory() || !DEFAULT_GRADLE_USER_HOME.exists()) {
                    gradleUserHome = osDataCandidate.getAbsolutePath();
                }
            }
        }
        if (gradleUserHome == null) {
            gradleUserHome = DEFAULT_GRADLE_USER_HOME.getAbsolutePath();
        }
        return canonicalize(new File(gradleUserHome));
    }

    static private @Nullable File osDataDirectory() {
        OperatingSystem os = OperatingSystem.current();
        File home = new File(System.getProperty("user.home"));
        if (os.isMacOsX()) {
            return new File(home, "Library/Application Support");
        } else if (os.isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isEmpty()) {
                return new File(localAppData);
            } else {
                return new File(home, "AppData/Local");
            }
        } else if (os.isUnix()) {
            // Linux, FreeBSD
            String dataHome = System.getenv("XDG_DATA_HOME");
            if (dataHome != null && !dataHome.isEmpty()) {
                return new File(dataHome);
            } else {
                return new File(home, ".local/share");
            }
        }
        return null;
    }

    @Nullable
    static private File findGradleInstallationHomeDir() {
        GradleInstallation gradleInstallation = CurrentGradleInstallation.get();
        if (gradleInstallation != null) {
            return gradleInstallation.getGradleHome();
        }
        return null;
    }

    public BuildLayoutParameters setProjectDir(@Nullable File projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    public BuildLayoutParameters setGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        return this;
    }

    public BuildLayoutParameters setGradleInstallationHomeDir(@Nullable File gradleInstallationHomeDir) {
        this.gradleInstallationHomeDir = gradleInstallationHomeDir;
        return this;
    }

    public BuildLayoutParameters setCurrentDir(File currentDir) {
        this.currentDir = currentDir;
        return this;
    }

    public File getCurrentDir() {
        return currentDir;
    }

    @Nullable
    public File getProjectDir() {
        return projectDir;
    }

    public File getSearchDir() {
        return projectDir != null ? projectDir : currentDir;
    }

    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    @Nullable
    public File getGradleInstallationHomeDir() {
        return gradleInstallationHomeDir;
    }
}
