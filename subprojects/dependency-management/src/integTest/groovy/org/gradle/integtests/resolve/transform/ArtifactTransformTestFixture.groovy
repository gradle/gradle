/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenModule

import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

trait ArtifactTransformTestFixture extends TasksWithInputsAndOutputs {
    abstract TestFile getBuildFile()

    /**
     * Defines a 'blue' variant for the given module.
     */
    MavenModule withColorVariants(MavenModule module) {
        module.adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata()
        return module
    }

    /**
     * Each project produces 'blue' variants and has a `resolve` task that resolves the 'green' variant. The 'blue' variant will contain
     * whatever is defined by the given closure on the supplied {@link Builder}.
     * By default each variant will contain a single file, this can be configured using the supplied {@link Builder}.
     * Caller will need to register transforms that produce 'green' from 'blue'
     */
    void setupBuildWithColorAttributes(@DelegatesTo(Builder) Closure cl = {}) {
        def builder = new Builder()
        builder.produceFiles()
        cl.delegate = builder
        cl.call()
        setupBuildWithColorAttributes(builder)
    }

    void setupBuildWithColorAttributes(Builder builder) {

        buildFile << """
import ${javax.inject.Inject.name}
// TODO: Default imports should work for of inner classes 
import ${org.gradle.api.artifacts.transform.TransformParameters.name}

def color = Attribute.of('color', String)
allprojects {
    configurations {
        implementation {
            attributes.attribute(color, 'blue')
        }
    }
    task producer(type: ${builder.producerTaskClassName}) {
        ${builder.producerConfig}
    }
    afterEvaluate {
        ${builder.producerConfigOverrides}
    }
    artifacts {
        implementation producer.output
    }
    task resolve {
        def view = configurations.implementation.incoming.artifactView {
            attributes.attribute(color, 'green')
        }.files
        inputs.files view
        doLast {
            println "result = \${view.files.name}"
        }
    }
}

import ${JarOutputStream.name}
import ${ZipEntry.name}

class JarProducer extends DefaultTask {
    @OutputFile
    final RegularFileProperty output = project.objects.fileProperty()
    @Input
    String content = "content"
    @Input
    long timestamp = 123L
    @Input
    String entryName = "thing.class"

    @TaskAction
    def go() {
        def file = output.get().asFile
        file.withOutputStream {
            def jarFile = new JarOutputStream(it)
            def entry = new ZipEntry(entryName)
            entry.time = timestamp
            jarFile.putNextEntry(entry)
            jarFile << content
            jarFile.close()
        }
    }
}
"""
        taskTypeWithOutputFileProperty()
        taskTypeWithOutputDirectoryProperty()
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a 'MakeGreen' transform that converts 'blue' to 'green'.
     * By default the variant will contain a single file, this can be configured using the supplied {@link Builder}.
     * Caller will need to provide an implementation of 'MakeGreen' transform action
     */
    void setupBuildWithColorTransformAction(@DelegatesTo(Builder) Closure cl = {}) {
        setupBuildWithColorAttributes(cl)
        buildFile << """
allprojects {
    dependencies {
        registerTransform(MakeGreen) {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
        }
    }
}
"""
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a 'MakeGreen' transform that converts 'blue' to 'green'.
     * By default the variant will contain a single file,  this can be configured using the supplied {@link Builder}.
     * Caller will need to provide an implementation of 'MakeGreen' transform configuration and use {@link TransformBuilder#params(java.lang.String)} to specify the configuration to
     * apply to the parameters.
     */
    void setupBuildWithColorTransform(@DelegatesTo(TransformBuilder) Closure cl = {}) {
        def builder = new TransformBuilder()
        cl.delegate = builder
        cl.call()

        setupBuildWithColorAttributes(builder)
        buildFile << """
allprojects { p ->
    dependencies {
        registerTransform(MakeGreen) {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
            ${builder.transformParamsConfig.empty ? "" : """
            parameters {
                ${builder.transformParamsConfig}
            }
            """}
        }
    }
}
"""
    }

    static class Builder {
        String producerTaskClassName
        String producerConfig
        String producerConfigOverrides

        Builder() {
            produceFiles()
        }

        /**
         * Specifies that each project produce a single file as output. Adds some system properties that can be used to override certain configuration:
         *
         * ${project.name}OutputDir - changes the build directory of the given project.
         * ${project.name}FileName - changes the output file name.
         * ${project.name}ProduceNothing - deletes the output file instead of writing to it.
         * ${project.name}Content - changes the text to write to the output file.
         */
        void produceFiles() {
            producerTaskClassName = "FileProducer"
            producerConfig = """
                output = layout.buildDir.file("\${project.name}.jar")
                content = project.name
            """.stripIndent()
            producerConfigOverrides = """
                if (project.hasProperty("\${project.name}OutputDir")) {
                    buildDir = project.file(project.property("\${project.name}OutputDir"))
                }
                tasks.withType(FileProducer) {
                    if (project.hasProperty("\${project.name}ProduceNothing")) {
                        content = ""
                    } else if (project.hasProperty("\${project.name}Content")) {
                        content = project.property("\${project.name}Content")
                    }
                    if (project.hasProperty("\${project.name}FileName")) {
                        output = layout.buildDir.file(project.property("\${project.name}FileName"))
                    }
                }
            """.stripIndent()
        }

        /**
         * Specifies that each project produce a single JAR as output.
         */
        void produceJars() {
            producerTaskClassName = "JarProducer"
            producerConfig = """
                output = layout.buildDir.file("\${project.name}.jar")
                content = project.name
            """.stripIndent()
            producerConfigOverrides = """
                if (project.hasProperty("\${project.name}OutputDir")) {
                    buildDir = project.file(project.property("\${project.name}OutputDir"))
                }
                tasks.withType(JarProducer) {
                    if (project.hasProperty("\${project.name}ProduceNothing")) {
                        content = ""
                    } else if (project.hasProperty("\${project.name}Content")) {
                        content = project.property("\${project.name}Content")
                    }
                    if (project.hasProperty("\${project.name}FileName")) {
                        output = layout.buildDir.file(project.property("\${project.name}FileName"))
                    }
                    if (project.hasProperty("\${project.name}Timestamp")) {
                        timestamp = Long.parseLong(project.property("\${project.name}Timestamp"))
                    }
                    if (project.hasProperty("\${project.name}EntryName")) {
                        entryName = project.property("\${project.name}EntryName")
                    }
                }
            """.stripIndent()
        }

        /**
         * Specifies that each project produce a single directory as output. Adds some system properties that can be used to override certain configuration:
         *
         * ${project.name}OutputDir - changes the build directory of the given project.
         * ${project.name}DirName - changes the output directory name.
         * ${project.name}ProduceNothing - deletes the output directory instead of writing to it.
         * ${project.name}Name - changes the name of the file to write to in the directory
         * ${project.name}Content - changes the text to write to the output file.
         */
        void produceDirs() {
            producerTaskClassName = "DirProducer"
            producerConfig = """
                output = layout.buildDir.dir("\${project.name}-dir")
                content = project.name  
                names = [project.name]
            """.stripIndent()
            producerConfigOverrides = """
                if (project.hasProperty("\${project.name}OutputDir")) {
                    buildDir = project.file(project.property("\${project.name}OutputDir"))
                }
                tasks.withType(DirProducer) {
                    if (project.hasProperty("\${project.name}ProduceNothing")) {
                        content = ""
                    } else if (project.hasProperty("\${project.name}Content")) {
                        content = project.property("\${project.name}Content")
                    }
                    if (project.hasProperty("\${project.name}Name")) {
                        names = [project.property("\${project.name}Name")]
                    }
                    if (project.hasProperty("\${project.name}Names")) {
                        names.set(project.property("\${project.name}Names").split(',') as List)
                    }
                    if (project.hasProperty("\${project.name}DirName")) {
                        output = layout.buildDir.dir(project.property("\${project.name}DirName"))
                    }
                }
            """.stripIndent()
        }
    }

    static class TransformBuilder extends Builder {
        String transformParamsConfig = ""

        /**
         * Specifies the configuration that should be applied to the parameters object of the 'blue' -> 'green' artifact transform
         * @param config
         */
        void params(String config) {
            transformParamsConfig = config
        }
    }
}
