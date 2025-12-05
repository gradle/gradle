/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.installation;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.classloader.ClasspathUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;

public final class CurrentGradleInstallationLocator {

    public static final String INTEGRATION_TEST_INSTALLATION_DIR_SYSTEM_PROPERTY = "integTest.gradleHomeDir";

    private static final String BEACON_CLASS_NAME = "org.gradle.internal.installation.beacon.InstallationBeacon";

    private CurrentGradleInstallationLocator() {
    }

    public synchronized static CurrentGradleInstallation locate() {
        CurrentGradleInstallation installation = locateViaClassLoader(CurrentGradleInstallationLocator.class.getClassLoader());
        if (installation != null) {
            return installation;
        }

//        String testHomeDir = System.getProperty(INTEGRATION_TEST_INSTALLATION_DIR_SYSTEM_PROPERTY);
//        if (testHomeDir != null) {
//            // We are not inside a distribution, but we are running in a test and a distribution has been provided.
//            return new CurrentGradleInstallation(new GradleInstallation(new File(testHomeDir)));
//        }

        return new CurrentGradleInstallation(null);
    }

    private @Nullable static CurrentGradleInstallation locateViaClassLoader(ClassLoader classLoader) {
        Class<?> clazz;
        try {
            clazz = classLoader.loadClass(BEACON_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            clazz = CurrentGradleInstallationLocator.class;
        }
        return locateViaClass(clazz);
    }

    @VisibleForTesting
    static @Nullable CurrentGradleInstallation locateViaClass(Class<?> clazz) {
        File dir = findDistDir(clazz);
        if (dir != null) {
            return new CurrentGradleInstallation(new GradleInstallation(dir));
        }

        return null;
    }

    private static File findDistDir(Class<?> clazz) {
        File codeSource = ClasspathUtil.getClasspathForClass(clazz);
        if (codeSource.isFile()) {
            return determineDistRootDir(codeSource);
        } else {
            // Loaded from a classes dir - assume we're running from the ide or tests
            return null;
        }
    }

    /**
     * Returns the root directory of a distribution based on the code source of a JAR file. The JAR can either sit in the lib or plugins subdirectory. Returns null if distribution doesn't have
     * expected directory layout.
     *
     * The expected directory layout for JARs of a distribution looks as such:
     *
     * dist-root
     * |_ lib
     * |_ plugins
     *
     * @param codeSource Code source of JAR file
     * @return Distribution root directory
     */
    private static File determineDistRootDir(File codeSource) {
        File parentDir = codeSource.getParentFile();

        if (parentDir.getName().equals("lib")) {
            File pluginsDir = new File(parentDir, "plugins");
            return parentDir.isDirectory() && pluginsDir.exists() && pluginsDir.isDirectory() ? parentDir.getParentFile() : null;
        }

        if (parentDir.getName().equals("plugins")) {
            File libDir = parentDir.getParentFile();
            return parentDir.isDirectory() && libDir.exists() && libDir.isDirectory() && libDir.getName().equals("lib") ? libDir.getParentFile() : null;
        }

        return null;
    }

}
