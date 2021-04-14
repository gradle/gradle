/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenModule

import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

trait ArtifactTransformTestFixture extends TasksWithInputsAndOutputs {
    abstract TestFile getBuildFile()

    abstract ExecutionResult getResult()

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
    void setupBuildWithColorAttributes(TestFile buildFile = getBuildFile(), @DelegatesTo(Builder) Closure cl = {}) {
        def builder = new Builder()
        builder.produceFiles()
        cl.delegate = builder
        cl.call()
        setupBuildWithColorAttributes(buildFile, builder)
    }

    void setupBuildWithColorAttributes(TestFile buildFile = getBuildFile(), Builder builder) {
        buildFile << """
import ${javax.inject.Inject.name}
// TODO: Default imports should work for of inner classes
import ${org.gradle.api.artifacts.transform.TransformParameters.name}

def color = Attribute.of('color', String)
allprojects {
    configurations {
        implementation {
            canBeResolved = true
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
    task resolve (type: ShowFileCollection) {
        def view = configurations.implementation.incoming.artifactView {
            attributes.attribute(color, 'green')
        }.files
        files.from(view)
    }
    task resolveArtifacts(type: ShowArtifactCollection) {
        collection = configurations.implementation.incoming.artifactView {
            attributes.attribute(color, 'green')
        }.artifacts
    }
}

import ${JarOutputStream.name}
import ${ZipEntry.name}

class ShowFileCollection extends DefaultTask {
    @InputFiles
    final ConfigurableFileCollection files = project.objects.fileCollection()

    ShowFileCollection() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    def go() {
        println "result = \${files.files.name}"
    }
}

class JarProducer extends DefaultTask {
    @OutputFile
    final RegularFileProperty output = project.objects.fileProperty()
    @Input
    final Property<String> content = project.objects.property(String).convention("content")
    @Input
    final Property<Long> timestamp = project.objects.property(Long).convention(123L)
    @Input
    final Property<String> entryName = project.objects.property(String).convention("thing.class")

    @TaskAction
    def go() {
        def file = output.get().asFile
        file.withOutputStream {
            println "write \${entryName.get()} with timestamp \${timestamp.get()} and content \${content.get()}"
            def jarFile = new JarOutputStream(it)
            try {
                def entry = new ZipEntry(entryName.get())
                entry.time = timestamp.get()
                jarFile.putNextEntry(entry)
                jarFile << content.get()
            } finally {
                jarFile.close()
            }
        }
    }
}
"""
        taskTypeWithOutputFileProperty(buildFile)
        taskTypeWithOutputDirectoryProperty(buildFile)
        taskTypeLogsArtifactCollectionDetails(buildFile)
    }

    /**
     * Asserts that exactly the given files where transformed by the 'simple' transforms below
     */
    void assertTransformed(String... fileNames) {
        assert result.output.findAll("processing \\[(.+)\\]").sort() == fileNames.collect { "processing [$it]" }.sort()
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'red'
     * and another transform that converts 'red' to 'green'.
     * By default the 'blue' variant will contain a single file, and the transform will produce a single 'green' file from this.
     */
    void setupBuildWithChainedColorTransform(boolean lenient = false) {
        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeColor) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'red')
                        parameters.targetColor.set('red')
                    }
                    registerTransform(MakeColor) {
                        from.attribute(color, 'red')
                        to.attribute(color, 'green')
                        parameters.targetColor.set('green')
                    }
                }
            }

            interface TargetColor extends TransformParameters {
                @Input
                Property<String> getTargetColor()
            }

            abstract class MakeColor implements TransformAction<TargetColor> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    assert ${lenient} || input.file
                    def output = outputs.file(input.name + "." + parameters.targetColor.get())
                    if (input.file) {
                        output.text = input.text + "-" + parameters.targetColor.get()
                    } else {
                        output.text = "missing-" + parameters.targetColor.get()
                    }
                }
            }
        """
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'green'
     * and that takes the 'red' variant as an input parameter.
     * By default the 'blue' variant will contain a single file, and the transform will produce a single 'green' file from this.
     */
    void setupBuildWithColorTransformWithAnotherTransformOutputAsInput() {
        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                configurations {
                   transform
                }
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                        parameters.inputFiles.from(configurations.transform.incoming.artifactView { attributes.attribute(color, 'red') }.files)
                    }
                    registerTransform(MakeRed) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'red')
                    }
                }
            }

            interface GreenParams extends TransformParameters {
                @InputFiles
                ConfigurableFileCollection getInputFiles()
            }

            abstract class MakeGreen implements TransformAction<GreenParams> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name} using \${parameters.inputFiles.files*.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + "-green"
                }
            }

            abstract class MakeRed implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name} to make red"
                    def output = outputs.file(input.name + ".red")
                    output.text = input.text + "-red"
                }
            }
        """
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'green'.
     * By default the 'blue' variant will contain a single file, and the transform will produce a single 'green' file from this.
     */
    void setupBuildWithLegacyColorTransformImplementation(TestFile buildFile = getBuildFile()) {
        setupBuildWithColorTransformAction(buildFile)
        buildFile << """
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    assert input.file
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'green'.
     * By default the 'blue' variant will contain a single file, and the transform will produce a single 'green' file from this.
     */
    void setupBuildWithColorTransformImplementation(TestFile buildFile = getBuildFile(), boolean lenient = false) {
        setupBuildWithColorTransform(buildFile)
        buildFile << """
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    assert ${lenient} || input.file
                    def output = outputs.file(input.name + ".green")
                    if (input.file) {
                        output.text = input.text + ".green"
                    } else {
                        output.text = "missing.green"
                    }
                }
            }
        """
    }

    void setupBuildWithColorTransformImplementation(boolean lenient) {
        setupBuildWithColorTransformImplementation(getBuildFile(), lenient)
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'green'.
     * By default the 'blue' variant will contain a single file, and the transform will produce a single 'green' file from this.
     */
    void setupBuildWithColorTransformThatTakesUpstreamArtifacts() {
        setupBuildWithColorTransformAction()
        buildFile << """
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifactDependencies
                abstract FileCollection getInputArtifactDependencies()

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    assert input.file
                    inputArtifactDependencies.files.each { assert it.file }
                    println "processing \${input.name} using \${inputArtifactDependencies.files*.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'red' and another from 'red' to 'green'.
     * The 'red' to 'green' transform also takes upstream dependencies.
     */
    void setupBuildWithChainedColorTransformThatTakesUpstreamArtifacts() {
        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeRed) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'red')
                    }
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'red')
                        to.attribute(color, 'green')
                    }
                }
            }

            abstract class MakeRed implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    assert input.file
                    println "processing [\${input.name}]"
                    def output = outputs.file(input.name + ".red")
                    output.text = input.text + "-red"
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifactDependencies
                abstract FileCollection getInputArtifactDependencies()

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    assert input.file
                    inputArtifactDependencies.files.each { assert it.file }
                    println "processing \${input.name} using \${inputArtifactDependencies.files*.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a 'MakeGreen' transform that converts 'blue' to 'green'.
     * By default the variant will contain a single file, this can be configured using the supplied {@link Builder}.
     * Caller will need to provide an implementation of 'MakeGreen' transform action
     */
    void setupBuildWithColorTransformAction(TestFile buildFile = getBuildFile(), @DelegatesTo(Builder) Closure cl = {}) {
        setupBuildWithColorAttributes(buildFile, cl)
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

    void setupBuildWithColorTransformAction(@DelegatesTo(Builder) Closure cl) {
        setupBuildWithColorTransformAction(buildFile, cl)
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a 'MakeGreen' transform that converts 'blue' to 'green'.
     * By default the variant will contain a single file,  this can be configured using the supplied {@link Builder}.
     * Caller will need to provide an implementation of 'MakeGreen' transform configuration and use {@link TransformBuilder#params(java.lang.String)} to specify the configuration to
     * apply to the parameters.
     */
    void setupBuildWithColorTransform(TestFile buildFile, @DelegatesTo(TransformBuilder) Closure cl = {}) {
        def builder = new TransformBuilder()
        cl.delegate = builder
        cl.call()

        setupBuildWithColorAttributes(buildFile, builder)
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

    void setupBuildWithColorTransform(@DelegatesTo(TransformBuilder) Closure cl = {}) {
        setupBuildWithColorTransform(buildFile, cl)
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
         * ${project.name}Content - changes the text to write to the output file, set to empty string to delete the output file.
         */
        void produceFiles() {
            producerTaskClassName = "FileProducer"
            // TODO - should not require forUseAtConfigurationTime()
            producerConfig = """
                output.convention(layout.buildDirectory.file(providers.gradleProperty("\${project.name}FileName").forUseAtConfigurationTime().orElse("\${project.name}.jar")))
                content.convention(providers.gradleProperty("\${project.name}Content").forUseAtConfigurationTime().orElse(project.name))
            """.stripIndent()
            // TODO - should not require forUseAtConfigurationTime()
            producerConfigOverrides = """
                layout.buildDirectory.convention(layout.projectDirectory.dir(providers.gradleProperty("\${project.name}OutputDir").forUseAtConfigurationTime().orElse("build")))
            """.stripIndent()
        }

        /**
         * Specifies that each project produce a single JAR as output.
         */
        void produceJars() {
            producerTaskClassName = "JarProducer"
            // TODO - should not require forUseAtConfigurationTime()
            producerConfig = """
                output.convention(layout.buildDirectory.file(providers.gradleProperty("\${project.name}FileName").forUseAtConfigurationTime().orElse("\${project.name}.jar")))
                content.convention(providers.gradleProperty("\${project.name}Content").orElse(project.name))
                timestamp.convention(providers.gradleProperty("\${project.name}Timestamp").map { Long.parseLong(it) }.orElse(123L))
                entryName.convention(providers.gradleProperty("\${project.name}EntryName").orElse("thing.class"))
            """.stripIndent()
            // TODO - should not require forUseAtConfigurationTime()
            producerConfigOverrides = """
                layout.buildDirectory.convention(layout.projectDirectory.dir(providers.gradleProperty("\${project.name}OutputDir").forUseAtConfigurationTime().orElse("build")))
                tasks.withType(JarProducer) {
                    if (project.hasProperty("\${project.name}ProduceNothing")) {
                        content = ""
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
            // TODO - should not require forUseAtConfigurationTime()
            producerConfig = """
                output.convention(layout.buildDirectory.dir(providers.gradleProperty("\${project.name}DirName").forUseAtConfigurationTime().orElse("\${project.name}-dir")))
                def defaultContent = project.name
                content.convention(providers.gradleProperty("\${project.name}Content").orElse(defaultContent))
                def defaultNames = [project.name]
                names.convention(providers.gradleProperty("\${project.name}Name").map { [it] }.orElse(defaultNames))
            """.stripIndent()
            // TODO - should not require forUseAtConfigurationTime()
            producerConfigOverrides = """
                layout.buildDirectory.convention(layout.projectDirectory.dir(providers.gradleProperty("\${project.name}OutputDir").forUseAtConfigurationTime().orElse("build")))
                tasks.withType(DirProducer) {
                    if (project.hasProperty("\${project.name}ProduceNothing")) {
                        content = ""
                    }
                    if (project.hasProperty("\${project.name}Names")) {
                        names.set(project.property("\${project.name}Names").split(',') as List)
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
