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

    void execute(Iterable<File> srcDirs, Iterable<String> includes, Iterable<String> excludes, File targetDir,
                 Iterable<File> classpathFiles, ScalaDocOptions docOptions) {

        ant.mkdir(dir: targetDir.absolutePath)

        Map options = ['destDir': targetDir] + docOptions.optionMap()
        if (logger.isInfoEnabled()) {
            StringBuilder builder = new StringBuilder()
            builder.append("Generating Scaladoc from source ${srcDirs}\n")
            builder.append("    options = ${options}\n")
            builder.append("    classpath = ${classpathFiles}\n")
            if (includes) {
                builder.append("    includes = ${includes}\n")
            }
            if (excludes) {
                builder.append("    excludes = ${excludes}\n")
            }
            if (bootclasspathFiles) {
                builder.append("    bootclasspath = ${bootclasspathFiles}\n")
            }
            if (extensionDirs) {
                builder.append("    extDirs = ${extensionDirs}\n")
            }
            logger.info(builder.toString())
        }

        ant.scaladoc(options) {
            srcDirs.each {dir ->
                src(location: dir)
            }
            bootclasspathFiles.each {file ->
                bootclasspath(location: file)
            }
            extensionDirs.each {dir ->
                extdirs(location: dir)
            }
            classpathFiles.each {file ->
                classpath(location: file)
            }
            includes.each {pattern ->
                include(name: pattern)
            }
            excludes.each {pattern ->
                exclude(name: pattern)
            }
        }
    }

}
