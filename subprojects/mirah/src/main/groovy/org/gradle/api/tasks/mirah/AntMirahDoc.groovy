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
package org.gradle.api.tasks.mirah

import org.gradle.api.file.FileCollection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.internal.project.IsolatedAntBuilder

class AntMirahDoc {
    private static Logger logger = LoggerFactory.getLogger(AntMirahDoc)

    private final IsolatedAntBuilder antBuilder
    private final Iterable<File> bootclasspathFiles
    private final Iterable<File> extensionDirs

    def AntMirahDoc(IsolatedAntBuilder antBuilder) {
        this.antBuilder = antBuilder
        this.bootclasspathFiles = []
        this.extensionDirs = []
    }

    def AntMirahDoc(IsolatedAntBuilder antBuilder, Iterable<File> bootclasspathFiles, Iterable<File> extensionDirs) {
        this.antBuilder = antBuilder
        this.bootclasspathFiles = bootclasspathFiles
        this.extensionDirs = extensionDirs
    }

    void execute(FileCollection source, File targetDir, Iterable<File> classpathFiles, Iterable<File> mirahClasspath, MirahDocOptions docOptions) {
        antBuilder.withClasspath(mirahClasspath).execute { ant ->
            taskdef(resource: 'mirah/tools/ant/antlib.xml')

            Map options = ['destDir': targetDir] + docOptions.optionMap()

            mirahdoc(options) {
                source.addToAntBuilder(ant, 'src', FileCollection.AntType.MatchingTask)
                bootclasspathFiles.each {file ->
                    bootclasspath(location: file)
                }
                extensionDirs.each {dir ->
                    extdirs(location: dir)
                }
                classpathFiles.each {file ->
                    classpath(location: file)
                }
            }
        }
    }
}
