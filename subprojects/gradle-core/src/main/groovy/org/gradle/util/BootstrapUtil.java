/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.util;

import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class BootstrapUtil {
    public static List<File> getGradleHomeLibClasspath() {
        File gradleHomeLib = new File(System.getProperty("gradle.home") + "/lib");
        if (gradleHomeLib.isDirectory()) {
            return Arrays.asList(gradleHomeLib.listFiles());
        }

        ClassLoader classLoader = BootstrapUtil.class.getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
            URL[] urls = urlClassLoader.getURLs();
            List<File> classpath = new ArrayList<File>();
            for (URL url : urls) {
                if (url.getProtocol().equals("file")) {
                    try {
                        classpath.add(new File(url.toURI()));
                    } catch (URISyntaxException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
            return classpath;
        }

        return Collections.emptyList();
    }

    public static List<File> getGradleClasspath() {
        File customGradleBin = null;
        List<File> pathElements = new ArrayList<File>();
        if (System.getProperty("gradle.bootstrap.gradleBin") != null) {
            customGradleBin = new File(System.getProperty("gradle.bootstrap.gradleBin"));
            pathElements.add(customGradleBin);
        }
        for (File homeLibFile : getGradleHomeLibClasspath()) {
            if (homeLibFile.exists() && !(customGradleBin != null && homeLibFile.getName().startsWith("gradle-"))) {
                pathElements.add(homeLibFile);
            }
        }
        return pathElements;
    }

    public static List<File> getGradleCoreFiles() {
        List<File> files = gradleLibClasspath("gradle-core");
        if (!files.isEmpty()) {
            return files;
        }

        // Look for a classes dir
        files = new ArrayList<File>();
        for (File file : getGradleClasspath()) {
            if (file.isDirectory()) {
                if (new File(file, BootstrapUtil.class.getName().replace('.', '/') + ".class").isFile()) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    public static List<File> getGroovyFiles() {
        return gradleLibClasspath("groovy", "antlr", "asm-all", "commons-cli");
    }

    public static List<File> getCommonsCliFiles() {
        return gradleLibClasspath("commons-cli");
    }

    public static List<File> getAntJunitJarFiles() {
        return gradleLibClasspath("ant", "ant-launcher", "ant-junit");
    }

    public static List<File> getAntJarFiles() {
        return gradleLibClasspath("ant", "ant-launcher");
    }

    public static List<File> gradleLibClasspath(String... prefixes) {
        List<File> result = new ArrayList<File>();
        for (File pathElement : getGradleClasspath()) {
            for (String searchPattern : prefixes) {
                if (pathElement.getName().startsWith(searchPattern + '-')) {
                    result.add(pathElement);
                }
            }
        }
        return result;
    }
}
