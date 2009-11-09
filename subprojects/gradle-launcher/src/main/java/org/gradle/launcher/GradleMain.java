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

package org.gradle.launcher;

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
public class GradleMain {

    public static void main(String[] args) throws Exception {
        String bootStrapDebugValue = System.getProperty("gradle.bootstrap.debug");
        boolean bootStrapDebug = bootStrapDebugValue != null && !bootStrapDebugValue.toUpperCase().equals("FALSE");

        processGradleHome(bootStrapDebug);

        List<URL> classpath = toUrl(BootstrapUtil.getGradleClasspath());
        ClassLoader parentClassloader = ClassLoader.getSystemClassLoader().getParent();
        if (bootStrapDebug) {
            System.out.println("Parent Classloader of new context classloader is: " + parentClassloader);
            System.out.println("Adding the following files to new lib classloader: " + classpath);
        }
        URLClassLoader libClassLoader = new URLClassLoader(classpath.toArray(new URL[classpath.size()]), parentClassloader);
        Thread.currentThread().setContextClassLoader(libClassLoader);
        Class mainClass = libClassLoader.loadClass("org.gradle.launcher.Main");
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, new Object[]{args});
    }

    private static void processGradleHome(boolean bootStrapDebug) {
        String gradleHome = System.getProperty("gradle.home");
        if (gradleHome == null) {
            gradleHome = System.getenv("GRADLE_HOME");
            if (gradleHome == null) {
                throw new RuntimeException("The gradle home must be set either via the system property gradle.home or via the environment variable GRADLE_HOME!");
            }
            if (bootStrapDebug) {
                System.out.println("Gradle Home is declared by environment variable GRADLE_HOME to: " + gradleHome);
            }
        } else {
            if (bootStrapDebug) {
                System.out.println("Gradle Home is declared by system property gradle.home to: " + gradleHome);
            }
        }
        System.setProperty("gradle.home", new File(gradleHome).getAbsolutePath());
    }
    
    private static List<URL> toUrl(List<File> files) throws MalformedURLException {
        List<URL> result = new ArrayList<URL>();
        for (File file : files) {
            result.add(file.toURI().toURL());
        }
        return result;
    }
}