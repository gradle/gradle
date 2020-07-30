/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.work.WorkerLeaseService

import javax.inject.Inject

@CacheableTask
abstract class Docbook2Xhtml extends SourceTask {
    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getSource() {
        return super.getSource()
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    abstract DirectoryProperty getStylesheetDirectory();

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    abstract ConfigurableFileCollection getDocbookStylesheets()

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    abstract RegularFileProperty getStylesheetHighlightFile();

    @Classpath
    abstract ConfigurableFileCollection getClasspath();

    @OutputDirectory
    abstract DirectoryProperty getDestinationDirectory();

    @Inject
    abstract WorkerLeaseService getWorkerLeaseService();

    @TaskAction
    def transform() {
        logging.captureStandardOutput(LogLevel.INFO)
        logging.captureStandardError(LogLevel.INFO)

        def destDir = destinationDirectory.get().asFile
        project.delete(destDir, temporaryDir)

        def xslClasspath = classpath.plus(project.files(ClasspathUtil.getClasspathForClass(XslTransformer)))

        project.copy {
            from(getStylesheetDirectory()) {
                include "**/*.xml"
                include "*.xsl"
            }
            from(project.zipTree(getDocbookStylesheets().singleFile)) {
                eachFile {
                    fcd -> fcd.path = fcd.path.replaceFirst("^docbook-xsl-[0-9\\.]+/", "")
                }
            }
            into(getTemporaryDir())
        }

        def stylesheetFile = new File(getTemporaryDir(), "dslHtml.xsl")
        def xslthlConfigFile = getStylesheetHighlightFile().get().asFile.toURI()

        // TODO: Implement this with the worker API
        workerLeaseService.withoutProjectLock({
            source.visit { FileVisitDetails fvd ->
                if (fvd.isDirectory()) {
                    return
                }
                if (!fvd.getFile().getName().endsWith(".xml")) {
                    return
                }

                String newFileName = fvd.file.name.replaceAll('.xml$', '.html')
                File outFile = fvd.relativePath.replaceLastName(newFileName).getFile(destDir)
                outFile.parentFile.mkdirs()

                project.javaexec {
                    main = XslTransformer.name
                    args stylesheetFile.absolutePath
                    args fvd.file.absolutePath
                    args outFile.absolutePath
                    args destDir.absolutePath
                    jvmArgs '-Xmx1024m'
                    classpath = xslClasspath
                    systemProperty 'xslthl.config', xslthlConfigFile
                    systemProperty 'org.apache.xerces.xni.parser.XMLParserConfiguration', 'org.apache.xerces.parsers.XIncludeParserConfiguration'
                }
            }
        } as Runnable)
    }
}
