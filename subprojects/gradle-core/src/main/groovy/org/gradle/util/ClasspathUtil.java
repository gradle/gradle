/*
 * Copyright 2010 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.net.URL;

/**
 * @author Hans Dockter
 */
public class ClasspathUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathUtil.class);

    public static void addUrl(URLClassLoader classLoader, Iterable<URL> classpathElements) {
        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            for (URL classpathElement : classpathElements) {
                method.invoke(classLoader, classpathElement);
            }
        }
        catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Error, could not add URL to system classloader", t);
        }
    }

    public static List<URL> getClasspath(ClassLoader classLoader) {
        List<URL> implementationClassPath = new ArrayList<URL>();
        for ( ClassLoader cl = classLoader;
                cl != ClassLoader.getSystemClassLoader().getParent(); cl = cl.getParent()) {
            if (cl instanceof URLClassLoader) {
                implementationClassPath.addAll(Arrays.asList(((URLClassLoader) cl).getURLs()));
            }
        }
        return implementationClassPath;
    }

    public static File getToolsJar() {
        String javaHome = System.getProperty("java.home");
        File toolsJar = new File(javaHome + "/lib/tools.jar");
        if (toolsJar.exists()) {
            LOGGER.debug("Found tools jar in: {}", toolsJar.getAbsolutePath());
            // Found in java.home as given
            return toolsJar;
        }
        if (javaHome.toLowerCase(Locale.US).endsWith(File.separator + "jre")) {
            javaHome = javaHome.substring(0, javaHome.length() - 4);
            toolsJar = new File(javaHome + "/lib/tools.jar");
        }
        if (!toolsJar.exists()) {
            LOGGER.debug("Unable to locate tools.jar. "
                    + "Expected to find it in " + toolsJar.getPath());
            return null;
        }
        LOGGER.debug("Found tools jar in: {}", toolsJar.getAbsolutePath());
        return toolsJar;
    }
}
