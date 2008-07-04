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
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Steven Devijver, Hans Dockter
 */
public class ToolsMain {
    public static void main(String[] args) throws Exception {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        List jars = new ArrayList();
        File[] files = getGradleClasspath();
        for (int i = 0; i < files.length; i++) {
            jars.add(files[i].toURL());
        }
        ClassLoader contextClassLoader = new URLClassLoader((URL[]) jars.toArray(new URL[jars.size()]), classLoader.getParent());
        classLoader = contextClassLoader;
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        Class mainClass = classLoader.loadClass("org.gradle.Main");
        Method mainMethod = mainClass.getMethod("main", new Class[]{String[].class});
        List argList = new ArrayList(Arrays.asList(args));
        argList.add(0, "-B Adding jars to context loader: " + jars);
        mainMethod.invoke(null, new Object[]
                {
                        (String[]) argList.toArray(new String[argList.size()])
                }
        );
    }

    private static File[] getGradleClasspath() {
        File gradleHomeLib = new File(System.getProperty("gradle.home") + "/lib");
        if (gradleHomeLib.isDirectory()) {
            return gradleHomeLib.listFiles();
        }
        return new File[0];
    }
}