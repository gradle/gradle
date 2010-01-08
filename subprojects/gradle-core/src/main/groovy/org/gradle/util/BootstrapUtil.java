/*
 * Copyright 2009 the original author or authors.
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
        File customGradleBin;
        List<File> pathElements = new ArrayList<File>();
        if (System.getProperty("gradle.bootstrap.gradleBin") != null) {
            customGradleBin = new File(System.getProperty("gradle.bootstrap.gradleBin"));
            pathElements.add(customGradleBin);
        }
        pathElements.addAll(getGradleHomeLibClasspath());
        return pathElements;
    }
}
