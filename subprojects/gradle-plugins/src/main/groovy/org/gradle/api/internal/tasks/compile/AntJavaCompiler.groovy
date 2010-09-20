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

package org.gradle.api.internal.tasks.compile

import org.gradle.api.AntBuilder
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.internal.Factory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class AntJavaCompiler implements JavaCompiler {
    private static Logger logger = LoggerFactory.getLogger(AntJavaCompiler)
    static final String CLASSPATH_ID = 'compile.classpath'
    FileCollection source;
    File destinationDir;
    Iterable<File> classpath;
    String sourceCompatibility;
    String targetCompatibility;
    CompileOptions compileOptions = new CompileOptions()
    final Factory<AntBuilder> antBuilderFactory

    def AntJavaCompiler(Factory<AntBuilder> antBuilderFactory) {
        this.antBuilderFactory = antBuilderFactory
    }

    void setDependencyCacheDir(File dir) {
        // don't care
    }

    WorkResult execute() {
        def ant = antBuilderFactory.create()
        
        createAntClassPath(ant, classpath)
        Map otherArgs = [
                includeAntRuntime: false,
                destdir: destinationDir,
                classpathref: CLASSPATH_ID,
                sourcepath: '',
                target: targetCompatibility,
                source: sourceCompatibility
        ]

        Map options = otherArgs + compileOptions.optionMap()
        logger.debug("Running ant javac with the following options {}", options)
        def task = ant.javac(options) {
            source.addToAntBuilder(ant, 'src', FileCollection.AntType.MatchingTask)
            compileOptions.compilerArgs.each {value ->
                compilerarg(value: value)
            }
        }

        int numFilesCompiled = task.fileList.length;
        return { numFilesCompiled > 0 } as WorkResult
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
