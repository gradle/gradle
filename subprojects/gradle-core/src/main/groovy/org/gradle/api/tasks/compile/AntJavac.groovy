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

package org.gradle.api.tasks.compile

import org.apache.tools.ant.taskdefs.Javac
import org.gradle.api.tasks.util.AntTaskAccess
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class AntJavac {
    private static Logger logger = LoggerFactory.getLogger(AntJavac)
    static final String CLASSPATH_ID = 'compile.classpath'

    private final AntTaskAccess listener = new AntTaskAccess() { task->
        if (task instanceof Javac) {
            numFilesCompiled = task.fileList.length;
        }
    }
    int numFilesCompiled;
    
    void execute(List sourceDirs, Collection includes, Collection excludes, File targetDir, File depCacheDir, 
                 Iterable classpath, String sourceCompatibility, String targetCompatibility,
                 CompileOptions compileOptions, AntBuilder ant) {
        createAntClassPath(ant, classpath)
        Map otherArgs = [
                includeAntRuntime: false,
                srcdir: sourceDirs.join(':'),
                destdir: targetDir,
                classpathref: CLASSPATH_ID,
                target: targetCompatibility,
                source: sourceCompatibility
        ]

        Map dependArgs = [
                srcDir : sourceDirs.join(':'),
                destDir: targetDir
        ]

        targetDir.mkdirs()

        Map dependOptions = dependArgs + compileOptions.dependOptions.optionMap()
        if (compileOptions.useDepend) {
            if (compileOptions.dependOptions.useCache) {
                dependOptions['cache'] = depCacheDir
            }
            logger.debug("Running ant depend with the following options {}", dependOptions)
            ant.depend(dependOptions) {
                includes.each {
                    include(name: it)
                }
                excludes.each {
                    exclude(name: it)
                }
            }
        }

        ant.project.addBuildListener(listener)
        Map options = otherArgs + compileOptions.optionMap()
        logger.debug("Running ant javac with the following options {}", options)
        ant.javac(options) {
            includes.each {
                include(name: it)
            }
            excludes.each {
                exclude(name: it)
            }
            compileOptions.compilerArgs.each { argValue ->
                argValue.each { key, value ->
                    compilerarg((key): value)
                }
            }
        }
        ant.project.removeBuildListener(listener)
    }

    private void createAntClassPath(AntBuilder ant, Iterable classpath) {
        ant.path(id: CLASSPATH_ID) {
            classpath.each {
                logger.debug("Add {} to Ant classpath!", it)
                pathelement(location: it)
            }
        }
    }
}
