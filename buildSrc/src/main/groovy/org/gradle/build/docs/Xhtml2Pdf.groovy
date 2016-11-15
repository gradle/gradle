/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
/**
 * Converts an Xhtml document into a PDF
 */
class Xhtml2Pdf extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    File sourceFile

    @OutputFile
    File destFile

    @Classpath
    FileCollection classpath

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection resources

    @TaskAction
    def transform() {
        def uris = classpath.files.collect {it.toURI().toURL()}
        def classloader = new URLClassLoader(uris as URL[], getClass().classLoader)
        def renderer = classloader.loadClass('org.xhtmlrenderer.pdf.ITextRenderer').newInstance()
        renderer.setDocument(sourceFile)
        renderer.layout()
        destFile.withOutputStream {
            renderer.createPDF(it)
        }
    }
}
