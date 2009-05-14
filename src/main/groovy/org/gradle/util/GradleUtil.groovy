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

import org.apache.commons.io.FilenameUtils
import org.apache.tools.ant.BuildListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class GradleUtil {
    private static Logger logger = LoggerFactory.getLogger(GradleUtil)

    static def List fileList(List fileDescriptions) {
        fileDescriptions.collect { new File(it.toString()) }
    }

    static def configure(Closure configureClosure, def delegate, int resolveStrategy) {
        if (!configureClosure) { return delegate}
        configureClosure.resolveStrategy = resolveStrategy
        configureClosure.delegate = delegate
        configureClosure.call()
        delegate
    }

    static void deleteDir(File dir) {
        assert !dir.isFile()
        if (dir.isDirectory()) {new AntBuilder().delete(dir: dir, quiet: true)}
    }

    static File makeNewDir(File dir) {
        deleteDir(dir)
        dir.mkdirs()
        dir.deleteOnExit()
        dir.canonicalFile
    }

    static URL[] filesToUrl(File file) {
        List files = [file]
        files.collect { it.toURL() }
    }

    static String unbackslash(def s) {
        FilenameUtils.separatorsToUnix(s.toString())
    }

    static String createIsolatedAntScript(String filling) {
        """ClassLoader loader = Thread.currentThread().contextClassLoader
AntBuilder ant = loader.loadClass('groovy.util.AntBuilder').newInstance()
ant.project.removeBuildListener(ant.project.getBuildListeners()[0])
ant.project.addBuildListener(loader.loadClass("org.gradle.logging.AntLoggingAdapter").newInstance())
ant.sequential {
    $filling
}
"""
    }

    static void replaceBuildListener(AntBuilder antBuilder, BuildListener buildListener) {
        antBuilder.project.removeBuildListener(antBuilder.getProject().getBuildListeners()[0])
        antBuilder.project.addBuildListener(buildListener)
    }

    static executeIsolatedAntScript(List loaderClasspath, String filling) {
        ClassLoader oldCtx = Thread.currentThread().contextClassLoader
        File toolsJar = ClasspathUtil.getToolsJar()
        logger.debug("Tools jar is: {}", toolsJar)
        List additionalClasspath = BootstrapUtil.nonLoggingJars
        if (toolsJar) {
            additionalClasspath.add(toolsJar)
        }
        URL[] taskUrlClasspath = (loaderClasspath + additionalClasspath).collect {File file ->
            file.toURI().toURL()
        }
        ClassLoader newLoader = new URLClassLoader(taskUrlClasspath, oldCtx.parent)
        Thread.currentThread().contextClassLoader = newLoader
        String scriptText = createIsolatedAntScript(filling)
        logger.debug("Using groovyc as: {}", scriptText)
        newLoader.loadClass("groovy.lang.GroovyShell").newInstance(newLoader).evaluate(
                scriptText)
        Thread.currentThread().contextClassLoader = oldCtx
    }

    static void setFromMap(Object object, Map args) {
        args.each { key, value ->
            object."$key" = value
        }
    }
}
