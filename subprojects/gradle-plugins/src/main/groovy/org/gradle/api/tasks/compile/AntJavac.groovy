/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.AntBuilder
import org.gradle.api.file.FileCollection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class AntJavac {
    private static Logger logger = LoggerFactory.getLogger(AntJavac)
    static final String CLASSPATH_ID = 'compile.classpath'

    int numFilesCompiled;

    void execute(FileCollection source, File targetDir, File depCacheDir,
                 Iterable classpath, String sourceCompatibility, String targetCompatibility,
                 CompileOptions compileOptions, AntBuilder ant) {
        createAntClassPath(ant, classpath)
        Map otherArgs = [
                includeAntRuntime: false,
                destdir: targetDir,
                classpathref: CLASSPATH_ID,
                sourcepath: '',
                target: targetCompatibility,
                source: sourceCompatibility
        ]

        Map dependArgs = [
                destDir: targetDir
        ]

        targetDir.mkdirs()

        if (compileOptions.useDepend) {
            Map dependOptions = dependArgs + compileOptions.dependOptions.optionMap()
            if (compileOptions.dependOptions.useCache) {
                dependOptions['cache'] = depCacheDir
            }
            logger.debug("Running ant depend with the following options {}", dependOptions)
            ant.project.addTaskDefinition('gradleDepend', AntDepend.class)
            ant.gradleDepend(dependOptions) {
                source.addToAntBuilder(ant, 'src', FileCollection.AntType.MatchingTask)
            }
        }

        Map options = otherArgs + compileOptions.optionMap()
        logger.debug("Running ant javac with the following options {}", options)
        def task = ant.javac(options) {
            source.addToAntBuilder(ant, 'src', FileCollection.AntType.MatchingTask)
            compileOptions.compilerArgs.each {value ->
                compilerarg(value: value)
            }
        }

        numFilesCompiled = task.fileList.length;
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
