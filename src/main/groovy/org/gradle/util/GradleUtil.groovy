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

package org.gradle.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.tools.ant.launch.Locator
import org.apache.commons.io.FilenameUtils

/**
 * @author Hans Dockter
 */
class GradleUtil {
    private static Logger logger = LoggerFactory.getLogger(GradleUtil)

    static Closure extractClosure(Object[] args) {
        if (args.length > 0 && args[args.length - 1] instanceof Closure) {
            return args[args.length - 1]
        }
        return null
    }

    static def List fileList(Object[] fileDescriptions) {
        fileDescriptions.collect { new File(it.toString()) }
    }

    static def configure(Closure configureClosure, def delegate, int resolveStrategy = Closure.DELEGATE_FIRST) {
        if (!configureClosure) { return delegate}
        configureClosure.resolveStrategy = resolveStrategy
        configureClosure.delegate = delegate
        configureClosure.call()
        delegate
    }

    static void deleteDir(File dir) {
        assert !dir.isFile()
        if (dir.isDirectory()) {new AntBuilder().delete(dir: dir)}
    }

    static File makeNewDir(File dir) {
        deleteDir(dir)
        dir.mkdir()
        dir.deleteOnExit()
        dir
    }

    static File[] getGradleClasspath() {
        File gradleHomeLib = new File(System.properties["gradle.home"] + "/lib")
        if (gradleHomeLib.isDirectory()) {
            return gradleHomeLib.listFiles()
        }
        []
    }

    static List getGroovyFiles() {
        gradleClasspath(['groovy-all'])
    }

    static List getAntJunitJarFiles() {
        gradleClasspath(['ant', 'ant-launcher', 'ant-junit'])
    }

    static List getAntJarFiles() {
        gradleClasspath(['ant', 'ant-launcher'])
    }

    static List gradleClasspath(List searchPatterns) {
        List path = getGradleClasspath() as List
        path.findAll {File file ->
            int pos = file.name.lastIndexOf('-')
            String libName = file.name.substring(0, pos)
            searchPatterns.contains(libName)
        }
    }

    static String unbackslash(def s) {
        FilenameUtils.separatorsToUnix(s.toString())
    }

    static setAntLogging(AntBuilder ant) {
        int logLevel = getAntLogLevel()
        logger.debug("Set ant loglevel to $logLevel")
        ant.project.getBuildListeners()[0].setMessageOutputLevel(logLevel)
    }

    static int getAntLogLevel() {
        int logLevel
        if (logger.isDebugEnabled()) {
            logLevel = org.apache.tools.ant.Project.MSG_DEBUG
        } else if (logger.isInfoEnabled()) {
            logLevel = org.apache.tools.ant.Project.MSG_INFO
        } else {
            logLevel = org.apache.tools.ant.Project.MSG_WARN
        }
        logLevel
    }

    static String createIsolatedAntScript(String filling) {
        """ClassLoader loader = Thread.currentThread().contextClassLoader
AntBuilder ant = loader.loadClass('groovy.util.AntBuilder').newInstance()
ant.project.getBuildListeners()[0].setMessageOutputLevel(${GradleUtil.antLogLevel})
ant.sequential {
    $filling
}
"""
    }

    static getToolsJar() {
        ClassLoader classLoader = Thread.currentThread().contextClassLoader.systemClassLoader
        // firstly check if the tools jar is already in the classpath
        boolean toolsJarAvailable = false;
        try {
            // just check whether this throws an exception
            classLoader.loadClass("com.sun.tools.javac.Main");
            toolsJarAvailable = true;
        } catch (Exception e) {
            try {
                classLoader.loadClass("sun.tools.javac.Main");
                toolsJarAvailable = true;
            } catch (Exception e2) {
                // ignore
            }
        }
        if (toolsJarAvailable) {
            return null;
        }
        // couldn't find compiler - try to find tools.jar
        // based on java.home setting
        String javaHome = System.getProperty("java.home");
        File toolsJar = new File(javaHome + "/lib/tools.jar");
        if (toolsJar.exists()) {
            // Found in java.home as given
            return toolsJar;
        }
        if (javaHome.toLowerCase(Locale.US).endsWith(File.separator + "jre")) {
            javaHome = javaHome.substring(0, javaHome.length() - 4);
            toolsJar = new File(javaHome + "/lib/tools.jar");
        }
        if (!toolsJar.exists()) {
            System.out.println("Unable to locate tools.jar. "
                    + "Expected to find it in " + toolsJar.getPath());
            return null;
        }
        return toolsJar;
    }

    static executeIsolatedAntScript(List loaderClasspath, String filling) {
        ClassLoader oldCtx = Thread.currentThread().contextClassLoader
        ClassLoader systemClassLoader = oldCtx.systemClassLoader
        if (getToolsJar()) {
            loaderClasspath << getToolsJar()
        }
        URL[] taskUrlClasspath = loaderClasspath.collect {
            Locator.fileToURL(it as File)
        }
        ClassLoader newLoader = new URLClassLoader(taskUrlClasspath, systemClassLoader.parent)
        newLoader.loadClass("com.sun.tools.javac.Main");
        Thread.currentThread().contextClassLoader = newLoader
        String scriptText = createIsolatedAntScript(filling)
        logger.debug("Using groovyc as: $scriptText")
        newLoader.loadClass("groovy.lang.GroovyShell").newInstance([newLoader] as Object[]).evaluate(
                scriptText)
        Thread.currentThread().contextClassLoader = oldCtx
    }
}
