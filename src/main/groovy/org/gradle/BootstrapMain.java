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

import org.gradle.util.BootstrapUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Steven Devijver, Hans Dockter
 */
public class BootstrapMain {

    public static void main(String[] args) throws Exception {
        String bootStrapDebugValue = System.getProperty("gradle.bootstrap.debug");
        boolean bootStrapDebug = bootStrapDebugValue != null && !bootStrapDebugValue.toUpperCase().equals("FALSE");

        processGradleHome(bootStrapDebug);

        List<URL> loggingJars = toUrl(BootstrapUtil.getLoggingJars());
        List<URL> nonLoggingJars = toUrl(BootstrapUtil.getNonLoggingJars());
        ClassLoader parentClassloader = ClassLoader.getSystemClassLoader().getParent();
        if (bootStrapDebug) {
            System.out.println("Parent Classloader of new context classloader is: " + parentClassloader);
            System.out.println("Adding the following files to new logging classloader: " + loggingJars);
            System.out.println("Adding the following files to new lib classloader: " + nonLoggingJars);
        }
        URLClassLoader loggingClassLoader = new URLClassLoader(loggingJars.toArray(new URL[loggingJars.size()]), parentClassloader);
        URLClassLoader libClassLoader = new URLClassLoader(nonLoggingJars.toArray(new URL[nonLoggingJars.size()]), loggingClassLoader);
        Thread.currentThread().setContextClassLoader(libClassLoader);
        Class mainClass = libClassLoader.loadClass("org.gradle.Main");
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, new Object[]{args});
    }

    private static String processGradleHome(boolean bootStrapDebug) {
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
        return gradleHome;
    }
    
    private static List<URL> toUrl(List<File> files) throws MalformedURLException {
        List<URL> result = new ArrayList<URL>();
        for (File file : files) {
            result.add(file.toURI().toURL());
        }
        return result;
    }
}