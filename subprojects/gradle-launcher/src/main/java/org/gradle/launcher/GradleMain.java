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

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathRegistry;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * @author Steven Devijver, Hans Dockter
 */
public class GradleMain {

    public static void main(String[] args) throws Exception {
        String bootStrapDebugValue = System.getProperty("gradle.bootstrap.debug");
        boolean bootStrapDebug = bootStrapDebugValue != null && !bootStrapDebugValue.toUpperCase().equals("FALSE");

        processGradleUserHome(bootStrapDebug);

        ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry();
        URL[] antClasspath = classPathRegistry.getClassPathUrls("ANT");
        URL[] runtimeClasspath = classPathRegistry.getClassPathUrls("GRADLE_RUNTIME");
        ClassLoader rootClassLoader = ClassLoader.getSystemClassLoader().getParent();
        if (bootStrapDebug) {
            System.out.println("Parent Classloader of new context classloader is: " + rootClassLoader);
            System.out.println("Using Ant classpath: " + Arrays.toString(antClasspath));
            System.out.println("Using runtime classpath: " + Arrays.toString(runtimeClasspath));
        }
        URLClassLoader antClassLoader = new URLClassLoader(antClasspath, rootClassLoader);
        URLClassLoader runtimeClassLoader = new URLClassLoader(runtimeClasspath, antClassLoader);
        Thread.currentThread().setContextClassLoader(runtimeClassLoader);
        Class mainClass = runtimeClassLoader.loadClass("org.gradle.launcher.Main");
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, new Object[]{args});
    }

    private static void processGradleUserHome(boolean bootStrapDebug) {
        String gradleUserHome = System.getProperty("gradle.user.home");
        if (gradleUserHome == null) {
            gradleUserHome = System.getenv("GRADLE_USER_HOME");
            if (gradleUserHome != null) {
                System.setProperty("gradle.user.home", gradleUserHome);
                if (bootStrapDebug) {
                    System.out.println("Gradle User Home is declared by environment variable GRADLE_USER_HOME to: " + gradleUserHome);
                }
            }
        }
    }
}