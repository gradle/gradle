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
import org.gradle.internal.Factory
import org.gradle.internal.jvm.Jvm
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AntJavaCompiler implements org.gradle.api.internal.tasks.compile.Compiler<JavaCompileSpec> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntJavaCompiler)
    private static final String CLASSPATH_ID = 'compile.classpath'

    private final Factory<AntBuilder> antBuilderFactory

    AntJavaCompiler(Factory<AntBuilder> antBuilderFactory) {
        this.antBuilderFactory = antBuilderFactory
    }

    WorkResult execute(JavaCompileSpec spec) {
        def ant = antBuilderFactory.create()

        createAntClassPath(ant, spec.classpath)
        Map otherArgs = [
                includeAntRuntime: false,
                destdir: spec.destinationDir,
                classpathref: CLASSPATH_ID,
                sourcepath: '',
                target: spec.targetCompatibility,
                source: spec.sourceCompatibility
        ]
        if (spec.compileOptions.fork && !spec.compileOptions.forkOptions.executable) {
            spec.compileOptions.forkOptions.executable = Jvm.current().javacExecutable
        }

        Map options = otherArgs + spec.compileOptions.optionMap()
        LOGGER.info("Compiling with Ant javac task.")
        LOGGER.debug("Ant javac task options: {}", options)
        def task = ant.javac(options) {
            spec.source.addToAntBuilder(ant, 'src', FileCollection.AntType.MatchingTask)
            spec.compileOptions.compilerArgs.each {value ->
                compilerarg(value: value)
            }
        }

        int numFilesCompiled = task.fileList.length;
        return { numFilesCompiled > 0 } as WorkResult
    }

    private void createAntClassPath(AntBuilder ant, Iterable classpath) {
        ant.path(id: CLASSPATH_ID) {
            classpath.each {
                LOGGER.debug("Add {} to Ant classpath!", it)
                pathelement(location: it)
            }
        }
    }
}
