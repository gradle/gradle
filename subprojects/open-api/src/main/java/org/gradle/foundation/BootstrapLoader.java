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
package org.gradle.foundation;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 This handles the work of loading gradle dynamically. Due to jar version issues,
 you can't just load all jar files.

 This does NOT require any system or environment variables to be set.

 To use this, instantiate this, then call one of the initialize functions.
 Now you can get the class loader to load whatever classes you like.

 @deprecated No replacement
  */
@Deprecated
public class BootstrapLoader {
    private URLClassLoader libClassLoader;

    public void initialize(File gradleHome, boolean bootStrapDebug) throws Exception {
        initialize(ClassLoader.getSystemClassLoader().getParent(), gradleHome, false, true, bootStrapDebug);
    }

    /*
      Call this to initialize gradle.
      @param  parentClassloader    a parent class loader. Probably whatever class loader
                                   is used by the caller.
      @param  gradleHome           the root directory where gradle is installed. This
                                   directory should have a 'bin' child directory.
      @param  useParentLastClassLoader true to use a class loader that will delegate
                                   to the parent only if it can't find it locally. This
                                   should only be true if you're trying to load gradle
                                   dynamically from another application.
      @param  loadOpenAPI          True to load the gradle open API, false not to.
                                   If you're calling this from a tool using the OpenAPI,
                                   then you've probably already loaded it, so pass in false
                                   here, otherwise, pass in true.
      @param  bootStrapDebug       true to output debug information about the loading
                                   process.
      @throws Exception            if something goes wrong.
   */
    public void initialize(ClassLoader parentClassloader, File gradleHome, boolean useParentLastClassLoader, boolean loadOpenAPI, boolean bootStrapDebug) throws Exception {
        if (gradleHome == null || !gradleHome.exists()) {
            throw new RuntimeException("Gradle home not defined!");
        }

        if (bootStrapDebug) {
            System.out.println("Gradle Home is declared by system property gradle.home to: " + gradleHome.getAbsolutePath());
        }

        System.setProperty("gradle.home", gradleHome.getAbsolutePath());

        List<URL> loggingJars = toUrl(getLoggingJars());

        List<File> nonLoggingJarFiles = getNonLoggingJars();
        removeUnwantedJarFiles(nonLoggingJarFiles, loadOpenAPI);
        List<URL> nonLoggingJars = toUrl(nonLoggingJarFiles);

        if (bootStrapDebug) {
            System.out.println("Parent Classloader of new context classloader is: " + parentClassloader);
            System.out.println("Adding the following files to new logging classloader: " + loggingJars);
            System.out.println("Adding the following files to new lib classloader: " + nonLoggingJars);
        }

        URLClassLoader loggingClassLoader = new URLClassLoader(loggingJars.toArray(new URL[loggingJars.size()]), parentClassloader);

        if (useParentLastClassLoader) {
            libClassLoader = new ParentLastClassLoader(nonLoggingJars.toArray(new URL[nonLoggingJars.size()]), loggingClassLoader);
        } else {
            libClassLoader = new URLClassLoader(nonLoggingJars.toArray(new URL[nonLoggingJars.size()]), loggingClassLoader);
        }

        if (bootStrapDebug) {
            System.out.println("Logging class loader: " + loggingClassLoader);
            System.out.println("Lib class loader: " + libClassLoader);
        }
    }

    public static File[] getGradleHomeLibClasspath() {
        File gradleHomeLib = new File(System.getProperty("gradle.home") + "/lib");
        if (gradleHomeLib.isDirectory()) {
            return gradleHomeLib.listFiles();
        }
        return new File[0];
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
            if (homeLibFile.isFile() && !(customGradleBin != null && homeLibFile.getName().startsWith("gradle-"))) {
                pathElements.add(homeLibFile);
            }
        }
        return pathElements;
    }

    /*
      This removes unwanted jar files. At the time of this writing, we're only
      interested in the open api jar.

      @param  nonLoggingJarFiles a list of jar files
      @param  loadOpenAPI        true to keep the open api jar, false to remove it.
   */
    private void removeUnwantedJarFiles(List<File> nonLoggingJarFiles, boolean loadOpenAPI) {
        if (loadOpenAPI) {
            return;
        }

        Iterator<File> iterator = nonLoggingJarFiles.iterator();
        while (iterator.hasNext()) {
            File file = iterator.next();
            if (file.getName().startsWith("gradle-open-api-")) {
                iterator.remove();
            }
        }
    }

    /*
      Call this to get the class loader you can use to load gradle classes.
      @return a URLClassLoader
   */
    public URLClassLoader getClassLoader() {
        return libClassLoader;
    }

    public Class load(String classPath) throws Exception {
        return libClassLoader.loadClass(classPath);
    }

    private static List<URL> toUrl(List<File> files) throws MalformedURLException {
        List<URL> result = new ArrayList<URL>();
        for (File file : files) {
            result.add(file.toURI().toURL());
        }
        return result;
    }
}
