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

package org.gradle;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Steven Devijver, Hans Dockter
 */
public class BootstrapMain {
    public static void main(String[] args) throws Exception {
        String bootStrapDebugValue = System.getProperty("gradle.bootstrap.debug");
        boolean bootStrapDebug = bootStrapDebugValue != null && !bootStrapDebugValue.toUpperCase().equals("FALSE");

        String gradleHome = System.getProperty("gradle.home");
        if (gradleHome == null) {
            gradleHome = System.getenv("GRADLE_HOME");
            if (gradleHome == null) {
                throw new RuntimeException("The gradle home must be set either via the system property gradle.home or via the environment variable GRADLE_HOME!");
            }
            if (bootStrapDebug) {
                System.out.println("Gradle Home is declared by environment variable GRADLE_HOME to: " + gradleHome);
            }                               
            System.setProperty("gradle.home", gradleHome);
        } else {
            if (bootStrapDebug) {
                System.out.println("Gradle Home is declared by system property gradle.home to: " + gradleHome);
            }
        }

        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        List jars = new ArrayList();
        File[] files = getGradleClasspath(gradleHome, bootStrapDebug);
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) {
                jars.add(files[i].toURL());
            }
        }
        ClassLoader parentClassloader = classLoader.getParent();
        if (bootStrapDebug) {
            System.out.println("Parent Classloader of new context classloader is: " + parentClassloader);
            System.out.println("Adding the following files to new contex classloader: " + jars);
        }
        ClassLoader contextClassLoader = new URLClassLoader((URL[]) jars.toArray(new URL[jars.size()]), parentClassloader);
        classLoader = contextClassLoader;
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        Class mainClass = classLoader.loadClass("org.gradle.Main");
        Method mainMethod = mainClass.getMethod("main", new Class[]{String[].class});
        mainMethod.invoke(null, new Object[] { args });
    }

    private static File[] getGradleClasspath(String gradleHome, boolean bootStrapDebug) {
        File gradleHomeLib = new File(gradleHome + "/lib");
        if (bootStrapDebug) {
            System.out.println("Gradle Home Lib: " + gradleHomeLib.getAbsolutePath());
        }
        if (gradleHomeLib.isDirectory()) {
            if (bootStrapDebug) {
                System.out.println("Gradle Home Lib is directory. Looking for files.");
            }
            return gradleHomeLib.listFiles();
        }
        System.out.println("WARNING: Gradle Home Lib is not a directory. Returning empty list as classpath.");
        return new File[0];
    }
}