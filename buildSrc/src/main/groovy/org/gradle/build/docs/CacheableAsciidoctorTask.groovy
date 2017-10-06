/*
 * Copyright 2017 the original author or authors.
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

import org.asciidoctor.gradle.AsciidoctorTask
import org.asciidoctor.gradle.AsciidoctorPlugin
import org.asciidoctor.gradle.AsciidoctorProxyImpl
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

@CacheableTask
class CacheableAsciidoctorTask extends AsciidoctorTask {

    /**
     * AsciidoctorTask annotates getOutputDirectories() with @OutputDirectories which returns a Set which breaks cacheability.
     * Since we use `separateOutputDirs = false`, getOutputDir() is sufficient and we can just ignore getOutputDirectories().
     */
    @Override
    @Internal
    Set<File> getOutputDirectories() {
        super.getOutputDirectories()
    }

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    File getSourceDir() {
        super.getSourceDir()
    }

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getSourceFileTree() {
        super.getSourceFileTree()
    }

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getResourceFileCollection() {
        super.getResourceFileCollection()
    }

    @Override
    void processAsciidocSources() {
        def originalCL =  Thread.currentThread().contextClassLoader
        // This is a copy of the initialization code of the super implementation that injects our custom AsciidoctorProxy implementation
        def classpath = project.configurations.getByName(AsciidoctorPlugin.ASCIIDOCTOR)
        def urls = classpath.files.collect { it.toURI().toURL() }
        def cl = new URLClassLoader(urls as URL[], Thread.currentThread().contextClassLoader)
        Thread.currentThread().contextClassLoader = cl
        if (!asciidoctorExtensions?.empty) {
            Class asciidoctorExtensionsDslRegistry = cl.loadClass('org.asciidoctor.groovydsl.AsciidoctorExtensions')
            asciidoctorExtensions.each { asciidoctorExtensionsDslRegistry.extensions(it) }
        }
        this.asciidoctor = new StreamClosingAsciidoctorProxyImpl(delegate: cl.loadClass(ASCIIDOCTOR_FACTORY_CLASSNAME).create(null as String))
        super.processAsciidocSources()
        Thread.currentThread().contextClassLoader = originalCL
    }

    static class StreamClosingAsciidoctorProxyImpl extends AsciidoctorProxyImpl {
        @Override
        String renderFile(File filename, Map<String, Object> options) {
            def content = filename.text
            options['to_file'] = filename.name.replace(".adoc", ".xml")
            delegate.render(content, options)
        }
    }
}
