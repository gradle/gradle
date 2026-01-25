/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.wrapper;

import org.gradle.internal.os.OperatingSystem;

import java.io.File;

public class GradleUserHomeLookup {
    public static File DEFAULT_GRADLE_USER_HOME = new File(System.getProperty("user.home") + "/.gradle");
    public static final String GRADLE_USER_HOME_PROPERTY_KEY = "gradle.user.home";
    public static final String GRADLE_USER_HOME_ENV_KEY = "GRADLE_USER_HOME";

    private static File osDataCandidate;
    public static File gradleUserHome() {
        String gradleUserHome = System.getProperty(GRADLE_USER_HOME_PROPERTY_KEY);
        if (gradleUserHome == null) {
            gradleUserHome = System.getenv(GRADLE_USER_HOME_ENV_KEY);
        }
        if (gradleUserHome == null) {
            if (osDataCandidate == null) {
                File osDataDirectory = osDataDirectory();
                if (osDataDirectory != null) {
                    osDataCandidate = new File(osDataDirectory, "Gradle");
                }
            }
            if (osDataCandidate != null) {
                if (osDataCandidate.isDirectory() || !DEFAULT_GRADLE_USER_HOME.exists()) {
                    gradleUserHome = osDataCandidate.getAbsolutePath();
                }
            }
        }
        if (gradleUserHome == null) {
            gradleUserHome = DEFAULT_GRADLE_USER_HOME.getAbsolutePath();
        }
        return new File(gradleUserHome);
    }

    public static File osDataDirectory() {
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
}
