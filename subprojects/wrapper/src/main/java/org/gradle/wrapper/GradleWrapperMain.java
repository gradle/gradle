/*
 * Copyright 2007 the original author or authors.
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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Hans Dockter
 */
public class GradleWrapperMain {
    public static final String ALWAYS_UNPACK_ENV = "GRADLE_WRAPPER_ALWAYS_UNPACK";
    public static final String ALWAYS_DOWNLOAD_ENV = "GRADLE_WRAPPER_ALWAYS_DOWNLOAD";
    public static final String DEFAULT_GRADLE_USER_HOME = System.getProperty("user.home") + "/.gradle";
    public static final String GRADLE_USER_HOME_PROPERTY_KEY = "gradle.user.home";
    public static final String GRADLE_USER_HOME_ENV_KEY = "GRADLE_USER_HOME";

    public static void main(String[] args) throws Exception {
        File wrapperJar = wrapperJar();
        File propertiesFile = wrapperProperties(wrapperJar);
        File rootDir = rootDir(wrapperJar);

        addSystemProperties(rootDir, args);

        boolean alwaysDownload = Boolean.parseBoolean(System.getenv(ALWAYS_DOWNLOAD_ENV));
        boolean alwaysUnpack = Boolean.parseBoolean(System.getenv(ALWAYS_UNPACK_ENV));

        WrapperExecutor.forWrapperPropertiesFile(propertiesFile, System.out).execute(
                args,
                new Install(alwaysDownload, alwaysUnpack, new Download(), new PathAssembler(gradleUserHome())),
                new BootstrapMainStarter());
    }

    private static void addSystemProperties(File rootDir, String[] args) {
        System.getProperties().putAll(SystemPropertiesHandler.getSystemProperties(args));
        System.getProperties().putAll(SystemPropertiesHandler.getSystemProperties(new File(gradleUserHome(), "gradle.properties")));
        System.getProperties().putAll(SystemPropertiesHandler.getSystemProperties(new File(rootDir, "gradle.properties")));
    }

    private static File rootDir(File wrapperJar) {
        return wrapperJar.getParentFile().getParentFile().getParentFile();
    }

    private static File wrapperProperties(File wrapperJar) {
        return new File(wrapperJar.getParent(), wrapperJar.getName().replaceFirst("\\.jar$", ".properties"));
    }

    private static File wrapperJar() {
        URI location;
        try {
            location = GradleWrapperMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        if (!location.getScheme().equals("file")) {
            throw new RuntimeException(String.format("Cannot determine classpath for wrapper Jar from codebase '%s'.", location));
        }
        return new File(location.getPath());
    }

    private static File gradleUserHome() {
        String gradleUserHome = System.getProperty(GRADLE_USER_HOME_PROPERTY_KEY);
        if (gradleUserHome != null) {
            return new File(gradleUserHome);
        } else if((gradleUserHome = System.getenv(GRADLE_USER_HOME_ENV_KEY)) != null) {
            return new File(gradleUserHome);
        } else {
            return new File(DEFAULT_GRADLE_USER_HOME);
        }
    }
}
