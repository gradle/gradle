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
package org.gradle.openapi.external;

import org.gradle.foundation.BootstrapLoader;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * Utility functions required by the OpenAPI
 * @deprecated No replacement
 */
@Deprecated
public class ExternalUtility {
    private static final Pattern GRADLE_CORE_PATTERN = Pattern.compile("^gradle-core-\\d.*\\.jar$");

    /**
     * Call this to get a classloader that has loaded gradle.
     *
     * @param parentClassLoader Your classloader. Probably the classloader of whatever class is calling this.
     * @param gradleHomeDirectory the root directory of a gradle installation
     * @param showDebugInfo true to show some additional information that may be helpful diagnosing problems is this fails
     * @return a classloader that has loaded gradle and all of its dependencies.
     */

    public static ClassLoader getGradleClassloader(ClassLoader parentClassLoader, File gradleHomeDirectory, boolean showDebugInfo) throws Exception {
        File gradleJarFile = getGradleJar(gradleHomeDirectory);
        if (gradleJarFile == null) {
            throw new RuntimeException("Not a valid gradle home directory '" + gradleHomeDirectory.getAbsolutePath() + "'");
        }

        System.setProperty("gradle.home", gradleHomeDirectory.getAbsolutePath());

        BootstrapLoader bootstrapLoader = new BootstrapLoader();
        bootstrapLoader.initialize(parentClassLoader, gradleHomeDirectory, true, false, showDebugInfo);
        return bootstrapLoader.getClassLoader();
    }

    /**
     * This locates the gradle jar. We do NOT want the gradle-wrapper jar.
     *
     * @param gradleHomeDirectory the root directory of a gradle installation. We're expecting this to have a child directory named 'lib'.
     * @return the gradle jar file. Null if we didn't find it.
     */
    public static File getGradleJar(File gradleHomeDirectory) {
        File libDirectory = new File(gradleHomeDirectory, "lib");
        if (!libDirectory.exists()) {
            return null;
        }

        //try to get the gradle.jar. It'll be "gradle-[version].jar"
        File[] files = libDirectory.listFiles(new FileFilter() {
            public boolean accept(File file) {
                return GRADLE_CORE_PATTERN.matcher(file.getName()).matches();
            }
        });

        if (files == null || files.length == 0) {
            return null;
        }

        //if they've given us a directory with multiple gradle jars, tell them. We won't know which one to use.
        if (files.length > 1) {
            throw new RuntimeException("Installation has multiple gradle jars. Cannot determine which one to use. Found files: " + createFileNamesString(files));
        }

        return files[0];
    }

    private static StringBuilder createFileNamesString(File[] files) {
        StringBuilder fileNames = new StringBuilder();
        for (File f : files) {
            fileNames.append(f.getAbsolutePath() + ", ");
        }
        fileNames.delete(fileNames.length() - 2, fileNames.length()); // Remove the trailing ', '
        return fileNames;
    }

    //just a function to help debugging. If we can't find the constructor we want, this dumps out what is available.

    public static String dumpConstructors(Class classInQuestion) {
        StringBuilder builder = new StringBuilder();
        Constructor[] constructors = classInQuestion.getConstructors();
        for (int index = 0; index < constructors.length; index++) {
            Constructor constructor = constructors[index];
            builder.append(constructor).append('\n');
        }

        return builder.toString();
    }

    public static String dumpMethods(Class classInQuestion) {
        StringBuilder builder = new StringBuilder();

        Method[] methods = classInQuestion.getMethods();
        for (int index = 0; index < methods.length; index++) {
            Method method = methods[index];
            builder.append(method).append('\n');
        }

        return builder.toString();
    }

    /**
     * This attempts to load the a class from the specified gradle home directory.
     *
     * @param classToLoad the full path to the class to load
     * @param parentClassLoader Your classloader. Probably the classloader of whatever class is calling this.
     * @param gradleHomeDirectory the root directory of a gradle installation
     * @param showDebugInfo true to show some additional information that may be helpful diagnosing problems is this fails
     */
    public static Class loadGradleClass(String classToLoad, ClassLoader parentClassLoader, File gradleHomeDirectory, boolean showDebugInfo) throws Exception {
        ClassLoader bootStrapClassLoader = getGradleClassloader(parentClassLoader, gradleHomeDirectory, showDebugInfo);
        Thread.currentThread().setContextClassLoader(bootStrapClassLoader);

        //load the class in gradle that wraps our return interface and handles versioning issues.
        try {
            return bootStrapClassLoader.loadClass(classToLoad);
        } catch (NoClassDefFoundError e) {  //might be a version mismatch
            e.printStackTrace();
            return null;
        } catch (Throwable e) {  //might be a version mismatch
            e.printStackTrace();
            return null;
        }
    }

    /**
     * This wraps up invoking a static method into a single call.
     *
     * @param classToInvoke the class that has the method
     * @param methodName the name of the method to invoke
     * @param argumentsClasses the classes of the arguments (we can't determine this from the argumentValues because they can be of class A, but implement class B and B is be the argument type of the
     * method in question
     * @param argumentValues the values of the arguments.
     * @return the return value of invoking the method.
     */
    public static Object invokeStaticMethod(Class classToInvoke, String methodName, Class[] argumentsClasses, Object... argumentValues) throws Exception {
        Method method = null;
        try {
            method = classToInvoke.getDeclaredMethod(methodName, argumentsClasses);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            System.out.println("Dumping available methods on " + classToInvoke.getName() + "\n" + ExternalUtility.dumpMethods(classToInvoke));
            throw e;
        }
        return method.invoke(null, argumentValues);
    }
}
