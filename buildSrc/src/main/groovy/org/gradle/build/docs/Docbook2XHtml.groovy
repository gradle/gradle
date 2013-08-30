/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.build.docs

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.api.tasks.*
import org.gradle.api.logging.LogLevel

class Docbook2Xhtml extends SourceTask {
    @InputFiles
    FileCollection classpath

    @OutputFile @Optional
    File destFile

    @OutputDirectory @Optional
    File destDir

    @InputDirectory
    File stylesheetsDir

    String stylesheetName

    @InputFiles @Optional
    FileCollection resources

    @TaskAction
    def transform() {
        if (!((destFile != null) ^ (destDir != null))) {
            throw new InvalidUserDataException("Must specify exactly 1 of output file or dir.")
        }

        logging.captureStandardOutput(LogLevel.INFO)
        logging.captureStandardError(LogLevel.INFO)

        source.visit { FileVisitDetails fvd ->
            if (fvd.isDirectory()) {
                return
            }

            File result
            if (destFile) {
                result = destFile
            } else {
                File outFile = fvd.relativePath.replaceLastName(fvd.file.name.replaceAll('.xml$', '.html')).getFile(destDir)
                outFile.parentFile.mkdirs()
                result = outFile
            }
            project.javaexec {
                main = XslTransformer.name
                args new File(stylesheetsDir, stylesheetName).absolutePath
                args fvd.file.absolutePath
                args result.absolutePath
                args destDir ?: ""
                jvmArgs '-Xmx256m'
                classpath ClasspathUtil.getClasspathForClass(XslTransformer)
                classpath this.classpath
                classpath new File(stylesheetsDir, 'extensions/xalan27.jar')
                systemProperty 'xslthl.config', new File("$stylesheetsDir/highlighting/xslthl-config.xml").toURI()
                systemProperty 'org.apache.xerces.xni.parser.XMLParserConfiguration', 'org.apache.xerces.parsers.XIncludeParserConfiguration'
            }
        }

        if (resources) {
            project.copy {
                into this.destDir ?: destFile.parentFile
                from resources
            }
        }
    }
}
