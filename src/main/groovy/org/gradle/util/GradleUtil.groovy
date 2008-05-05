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

/**
 * @author Hans Dockter
 */
class GradleUtil {
    private static Logger logger = LoggerFactory.getLogger(GradleUtil)

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

    static createIsolatedAntScript(String filling) {
        String groovyc = """
ClassLoader loader = Thread.currentThread().contextClassLoader
AntBuilder ant = loader.loadClass('groovy.util.AntBuilder').newInstance()
ant.project.getBuildListeners()[0].setMessageOutputLevel(${GradleUtil.antLogLevel})
ant.sequential {
    $filling
}
"""
    }

    static executeIsolatedAntScript(List loaderClasspath, String filling) {
        URL[] taskUrlClasspath = loaderClasspath.collect {
            Locator.fileToURL(it as File)
        }
        ClassLoader oldCtx = Thread.currentThread().contextClassLoader
        ClassLoader newLoader = new URLClassLoader(taskUrlClasspath, GradleUtil.class.classLoader.systemClassLoader.parent)
        Thread.currentThread().contextClassLoader = newLoader
        newLoader.loadClass("groovy.lang.GroovyShell").newInstance([newLoader] as Object[]).evaluate(
                createIsolatedAntScript(filling))
        Thread.currentThread().contextClassLoader = oldCtx
    }
}
