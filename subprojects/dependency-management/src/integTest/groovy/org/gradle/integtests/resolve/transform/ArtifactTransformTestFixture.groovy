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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenModule

trait ArtifactTransformTestFixture {
    abstract TestFile getBuildFile()

    /**
     * Defines a 'blue' variant for the given module.
     */
    MavenModule withColorVariants(MavenModule module) {
        module.adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata()
        return module
    }

    /**
     * Each project produces a 'blue' variant containing a single file, and has a `resolve` task that resolves the 'green' variant.
     * Caller will need to register transforms that produce 'green' from 'blue'
     */
    void setupBuildWithColorAttributes() {
        setupBuildWithColorAttributes(new Builder())
    }

    /**
     * Each project produces 'blue' variants and has a `resolve` task that resolves the 'green' variant. The 'blue' variant will contain
     * whatever is defined by the given closure on the supplied {@link Builder}. Defaults to producing a file.
     * Caller will need to register transforms that produce 'green' from 'blue'
     */
    void setupBuildWithColorAttributes(@DelegatesTo(Builder) Closure cl) {
        def builder = new Builder()
        builder.produceFiles()
        cl.delegate = builder
        cl.call()
        setupBuildWithColorAttributes(builder)
    }

    void setupBuildWithColorAttributes(Builder builder) {

        buildFile << """
import ${javax.inject.Inject.name}

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

class FileProducer extends DefaultTask {
    @OutputFile
    final RegularFileProperty output = project.objects.fileProperty()
    @Input
    String content = "content" // set to empty string to delete file

    @TaskAction
    def go() {
        def file = output.get().asFile
        if (content.empty) {
            file.delete()
        } else {
            file.text = content
        }
    }
}

class DirProducer extends DefaultTask {
    @OutputDirectory
    final DirectoryProperty output = project.objects.directoryProperty()
    @Input
    final ListProperty<String> names = project.objects.listProperty(String)
    @Input
    String content = "content" // set to empty string to delete directory

    @TaskAction
    def go() {
        def dir = output.get().asFile
        if (content.empty) {
            project.delete(dir)
        } else {
            project.delete(dir)
            dir.mkdirs()
            names.get().forEach {
                new File(dir, it).text = content
            }
        }
    }
}
"""
    }

    /**
     * Each project produces a 'blue' variant that contains a single file, and has a `resolve` task that resolves the 'green' variant and a 'MakeGreen' transform that converts 'blue' to 'green'
     * Caller will need to provide an implementation of 'MakeGreen' transform action
     */
    void setupBuildWithColorTransformAction() {
        setupBuildWithColorTransformAction {}
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a 'MakeGreen' transform that converts 'blue' to 'green'
     * Caller will need to provide an implementation of 'MakeGreen' transform action
     */
    void setupBuildWithColorTransformAction(@DelegatesTo(Builder) Closure cl) {
        setupBuildWithColorAttributes(cl)
        buildFile << """
allprojects {
    dependencies {
        registerTransformAction(MakeGreen) {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
        }
    }
}
"""
    }

    /**
     * Each project produces a 'blue' variant containing a single file, and has a `resolve` task that resolves the 'green' variant and a 'MakeGreen' transform that converts 'blue' to 'green'
     * Caller will need to provide an implementation of 'MakeGreen' transform configuration. Does not apply any configuration to this type.
     */
    void setupBuildWithColorTransform() {
        setupBuildWithColorTransform {}
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a 'MakeGreen' transform that converts 'blue' to 'green'
     * Caller will need to provide an implementation of 'MakeGreen' transform configuration and use {@link TransformBuilder#params(java.lang.String)} to specify the configuration to
     * apply to the parameters.
     */
    void setupBuildWithColorTransform(@DelegatesTo(TransformBuilder) Closure cl) {
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
            parameters {
                ${builder.transformParamsConfig}
            }
        }
    }
}
"""
    }

    static class Builder {
        String producerTaskClassName
        String producerConfig

        Builder() {
            produceFiles()
        }

        /**
         * Specifies that each project produce a single file as output.
         */
        void produceFiles() {
            producerTaskClassName = "FileProducer"
            producerConfig = """
                output = layout.buildDir.file("\${project.name}.jar")
            """.stripIndent()
        }

        /**
         * Specifies that each project produce a single directory as output.
         */
        void produceDirs() {
            producerTaskClassName = "DirProducer"
            producerConfig = """
                output = layout.buildDir.dir("\${project.name}-dir")
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
