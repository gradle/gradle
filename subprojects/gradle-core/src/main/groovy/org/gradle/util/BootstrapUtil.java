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
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.net.URLClassLoader;
import java.net.URL;
import java.net.URISyntaxException;

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
            List<File> classpath = new ArrayList();
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

    public static List<File> getNonLoggingJars() {
        List<File> pathElements = new ArrayList<File>();
        for (File file : getGradleClasspath()) {
            if (!isLogLib(file)) {
                pathElements.add(file);
            }
        }
        return pathElements;
    }

    public static List<File> getLoggingJars() {
        List<File> pathElements = new ArrayList<File>();
        for (File file : getGradleClasspath()) {
            if (isLogLib(file)) {
                pathElements.add(file);
            }
        }
        return pathElements;
    }

    private static boolean isLogLib(File file) {
        return file.getName().startsWith("logback") || file.getName().startsWith("slf4j");
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

    public static List<File> getGroovyFiles() {
        return gradleLibClasspath(WrapUtil.toList("groovy"));
    }

    public static List<File> getAntJunitJarFiles() {
        return gradleLibClasspath(WrapUtil.toList("ant", "ant-launcher", "ant-junit"));
    }

    public static List<File> getAntJarFiles() {
        return gradleLibClasspath(WrapUtil.toList("ant", "ant-launcher"));
    }

    public static List<File> gradleLibClasspath(List searchPatterns) {
        List<File> result = new ArrayList<File>();
        for (File pathElement : getGradleClasspath()) {
            int pos = pathElement.getName().lastIndexOf("-");
            if (pos == -1) {
                continue;
            }
            String libName = pathElement.getName().substring(0, pos);
            if (searchPatterns.contains(libName)) {
                result.add(pathElement);
            }
        }
        return result;
    }
}
