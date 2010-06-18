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
package org.gradle.api.tasks.scala

import org.gradle.api.file.FileCollection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AntScalaDoc {
    private static Logger logger = LoggerFactory.getLogger(AntScalaDoc)

    private final AntBuilder ant
    private final Iterable<File> bootclasspathFiles
    private final Iterable<File> extensionDirs

    def AntScalaDoc(AntBuilder ant) {
        this.ant = ant
        this.bootclasspathFiles = []
        this.extensionDirs = []
    }

    def AntScalaDoc(AntBuilder ant, Iterable<File> bootclasspathFiles, Iterable<File> extensionDirs) {
        this.ant = ant
        this.bootclasspathFiles = bootclasspathFiles
        this.extensionDirs = extensionDirs
    }

    void execute(FileCollection source, File targetDir, Iterable<File> classpathFiles, Iterable<File> scalaClasspath, ScalaDocOptions docOptions) {

        ant.taskdef(resource: 'scala/tools/ant/antlib.xml') {
            scalaClasspath.each {file ->
                classpath(location: file)
            }
        }

        Map options = ['destDir': targetDir] + docOptions.optionMap()

        ant.scaladoc(options) {
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
