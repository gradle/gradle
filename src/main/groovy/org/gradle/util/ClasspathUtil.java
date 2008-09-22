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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.net.URL;

/**
 * @author Hans Dockter
 */
public class ClasspathUtil {
    private static Logger logger = LoggerFactory.getLogger(ClasspathUtil.class);

    public static File[] getGradleClasspath() {
        File gradleHomeLib = new File(System.getProperty("gradle.home") + "/lib");
        if (gradleHomeLib.isDirectory()) {
            return gradleHomeLib.listFiles();
        }
        return new File[0];
    }

    public static void addUrl(URLClassLoader classLoader, List<File> classpathElements) {
        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            for (File classpathElement : classpathElements) {
                logger.debug("Adding to classpath: " + classpathElement);
                method.invoke(classLoader, classpathElement.toURI().toURL());
            }
        }
        catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Error, could not add URL to system classloader", t);
        }
    }
}
