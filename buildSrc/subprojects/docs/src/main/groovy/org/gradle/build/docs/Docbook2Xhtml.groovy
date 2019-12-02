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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.logging.ConsoleRenderer

@CacheableTask
abstract class Docbook2Xhtml extends SourceTask {
    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getSource() {
        return super.getSource()
    }

    @OutputDirectory
    abstract DirectoryProperty getDestinationDirectory();

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    abstract RegularFileProperty getStylesheetFile();

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    abstract RegularFileProperty getStylesheetHighlightFile();

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    abstract ConfigurableFileCollection getResources();

    @Classpath
    abstract ConfigurableFileCollection getClasspath();

    @TaskAction
    def transform() {
        logging.captureStandardOutput(LogLevel.INFO)
        logging.captureStandardError(LogLevel.INFO)

        // TODO: Implement this with the worker API
        def xslClasspath = classpath + ClasspathUtil.getClasspathForClass(XslTransformer)
        def xslthlConfigFile = getStylesheetHighlightFile().get().asFile.toURI()

        def destDir = destinationDirectory.get.asFile
        source.visit { FileVisitDetails fvd ->
            if (fvd.isDirectory()) {
                return
            }

            String newFileName = fvd.file.name.replaceAll('.xml$', '.html')
            File outFile = fvd.relativePath.replaceLastName(newFileName).getFile(destDir)
            outFile.parentFile.mkdirs()

            project.javaexec {
                main = XslTransformer.name
                args stylesheetFile.get().asFile.absolutePath
                args fvd.file.absolutePath
                args outFile.absolutePath
                args destDir.absolutePath
                jvmArgs '-Xmx1024m'
                classpath = xslClasspath
                systemProperty 'xslthl.config', xslthlConfigFile
                systemProperty 'org.apache.xerces.xni.parser.XMLParserConfiguration', 'org.apache.xerces.parsers.XIncludeParserConfiguration'
            }
            logger.lifecycle("$name available at ${new ConsoleRenderer().asClickableFileUrl(outFile)}")
        }
        project.copy {
            from resources
            into destDir
        }
    }
}
