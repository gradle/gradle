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

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.resolve.VariantAwareDependencyResolutionTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenModule

import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * Sets up builds whose projects produce and consume color variants.
 *
 * The fixture declares its classes in the settings script and configures each project from there with
 * {@code gradle.lifecycle.beforeProject}, which works with Isolated Projects. Classes declared in the settings script
 * are not visible to build scripts, so tests declare the classes that the configuration applied to each project uses with
 * {@link #settingsScriptClasses(java.lang.String)}. The color attribute is available to build scripts as the {@code color} extra property.
 */
@SelfType(AbstractIntegrationSpec)
trait ArtifactTransformTestFixture extends VariantAwareDependencyResolutionTestFixture {
    abstract TestFile getSettingsFile()

    abstract ExecutionResult getResult()

    /**
     * Defines a 'blue' variant for the given module.
     */
    def <T extends MavenModule> T withColorVariants(T module) {
        module.adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata()
        return module
    }

    /**
     * Each project produces 'blue' variants and has a `resolve` task that resolves the 'green' variant. The 'blue' variant will contain
     * whatever is defined by the given closure on the supplied {@link Builder}.
     * By default each variant will contain a single file, this can be configured using the supplied {@link Builder}.
     * Caller will need to register transforms that produce 'green' from 'blue'
     */
    void setupBuildWithColorAttributes(TestFile settingsFile = getSettingsFile(), @DelegatesTo(Builder) Closure cl = {}) {
        def builder = new Builder()
        builder.produceFiles()
        cl.delegate = builder
        cl.call()
        setupBuildWithColorAttributes(settingsFile, builder)
    }

    void setupBuildWithColorAttributes(TestFile settingsFile = getSettingsFile(), Builder builder) {
        withScriptImports(settingsFile)
        this.settingsFile(settingsFile, jarProducerTask() + showFileCollectionTask())
        taskTypeWithOutputFileProperty(settingsFile)
        taskTypeWithOutputDirectoryProperty(settingsFile)
        taskTypeLogsArtifactCollectionDetails(settingsFile)
        configureEachProject(settingsFile, """
            def color = Attribute.of('color', String)
            // Lets build scripts refer to the attribute as 'color'
            ext.color = color
            ${colorVariantConfigurations()}
            tasks.create('producer', ${builder.producerTaskClassName}) {
                ${builder.producerConfig}
            }
            afterEvaluate {
                ${builder.producerConfigOverrides}
            }
            artifacts {
                implementation tasks.producer.output
            }

            tasks.create('resolve', ShowFileCollection) {
                def view = configurations.resolver.incoming.artifactView {
                    attributes.attribute(color, 'green')
                }.files
                files.from(view)
            }
            tasks.create('resolveArtifacts', ShowArtifactCollection) {
                collection = configurations.resolver.incoming.artifactView {
                    attributes.attribute(color, 'green')
                }.artifacts
            }
        """)
    }

    private String jarProducerTask() {
        """
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
    }

    /**
     * Declares the given classes in the given settings script, so that the configuration
     * applied to each project with {@link #configureEachProject(java.lang.String)} can use them.
     */
    void settingsScriptClasses(TestFile settingsFile = getSettingsFile(), String classes) {
        this.settingsFile(withScriptImports(settingsFile), classes)
    }

    /**
     * Applies the given configuration to each project of the build with the given settings script, before its build script runs,
     * as {@code allprojects {}} in the root build script would have configured it.
     * It runs with the project as its delegate. As it is not a build script, it declares tasks with {@code tasks.create()} rather than {@code task name(type: T)}.
     */
    void configureEachProject(String configuration) {
        configureEachProject(getSettingsFile(), configuration)
    }

    void configureEachProject(TestFile settingsFile, String configuration) {
        this.settingsFile(withScriptImports(settingsFile), """
            gradle.lifecycle.beforeProject { project ->
                project.with {
                    ${configuration}
                }
            }
        """)
    }

    /**
     * Adds the imports that are not default imports of scripts to the given settings script, once.
     */
    private TestFile withScriptImports(TestFile settingsFile) {
        def imports = """
            import ${javax.inject.Inject.name}
            // TODO: Default imports should work for of inner classes
            import ${org.gradle.api.artifacts.transform.TransformParameters.name}
            import ${JarOutputStream.name}
            import ${ZipEntry.name}
        """
        if (!settingsFile.exists() || !settingsFile.text.contains(imports)) {
            this.settingsFile(settingsFile, imports)
        }
        settingsFile
    }

    /**
     * Adds the given configuration to each project, and declares the given classes.
     */
    private void addToAllProjects(TestFile settingsFile, String configuration, String classes) {
        settingsScriptClasses(settingsFile, classes)
        if (!configuration.trim().empty) {
            configureEachProject(settingsFile, configuration)
        }
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
        addToAllProjects(settingsFile, """
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
""", """
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
""")
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'green'
     * and that takes the 'red' variant as an input parameter.
     * By default the 'blue' variant will contain a single file, and the transform will produce a single 'green' file from this.
     */
    void setupBuildWithColorTransformWithAnotherTransformOutputAsInput() {
        setupBuildWithColorAttributes()
        addToAllProjects(settingsFile, """
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
""", """
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
""")
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'green'.
     * By default the 'blue' variant will contain a single file, and the transform will produce a single 'green' file from this.
     */
    void setupBuildWithColorTransformImplementation(TestFile settingsFile = getSettingsFile(), boolean lenient = false) {
        setupBuildWithColorTransform(settingsFile)
        addToAllProjects(settingsFile, "", """
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
""")
    }

    void setupBuildWithColorTransformImplementation(boolean lenient) {
        setupBuildWithColorTransformImplementation(getSettingsFile(), lenient)
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'green'.
     * By default the 'blue' variant will contain a single file, and the transform will produce a single 'green' file from this.
     */
    void setupBuildWithColorTransformThatTakesUpstreamArtifacts() {
        setupBuildWithColorTransform()
        addToAllProjects(settingsFile, "", """
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
""")
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'red' and another from 'red' to 'green'.
     * The 'red' to 'green' transform also takes upstream dependencies.
     */
    void setupBuildWithChainedColorTransformThatTakesUpstreamArtifacts() {
        setupBuildWithColorAttributes()
        addToAllProjects(settingsFile, """
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
""", """
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
""")
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a 'MakeGreen' transform that converts 'blue' to 'green'.
     * By default the variant will contain a single file,  this can be configured using the supplied {@link Builder}.
     * Caller will need to provide an implementation of 'MakeGreen' transform configuration and use {@link TransformBuilder#params(java.lang.String)} to specify the configuration to
     * apply to the parameters.
     */
    void setupBuildWithColorTransform(TestFile settingsFile, @DelegatesTo(TransformBuilder) Closure cl = {}) {
        def builder = new TransformBuilder()
        cl.delegate = builder
        cl.call()

        setupBuildWithColorAttributes(settingsFile, builder)
        addToAllProjects(settingsFile, """
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
""", "")
    }

    void setupBuildWithColorTransform(@DelegatesTo(TransformBuilder) Closure cl = {}) {
        setupBuildWithColorTransform(getSettingsFile(), cl)
    }

    static class ProjectConfigurator {
        private final GradleExecuter executer
        private final String projectName

        private ProjectConfigurator(GradleExecuter executer, String projectName) {
            this.projectName = projectName
            this.executer = executer
        }

        void setProjectBuildDir(String buildDirPath) {
            executer.withArgument("-D${projectName}OutputDir=${buildDirPath}")
        }

        void setOutputFileName(String outputFileName) {
            executer.withArgument("-D${projectName}FileName=${outputFileName}")
        }

        void setOutputDirName(String outputDirName) {
            executer.withArgument("-D${projectName}DirName=${outputDirName}")
        }

        void emptyOutputDir() {
            executer.withArgument("-D${projectName}EmptyDir")
        }

        void setOutputFileContent(String textToWrite) {
            executer.withArgument("-D${projectName}Content=${textToWrite}")
        }

        void setOutputJarEntryName(String entryName) {
            executer.withArgument("-D${projectName}EntryName=${entryName}")
        }

        void setOutputJarEntryTimestamp(int timestamp) {
            executer.withArgument("-D${projectName}Timestamp=${timestamp}")
        }

        void setNames(List<String> names) {
            executer.withArgument("-D${projectName}Names=${names.join(',')}")
        }

        void produceNothing() {
            executer.withArgument("-D${projectName}ProduceNothing")
        }
    }

    void withProjectConfig(String projectName, @DelegatesTo(ProjectConfigurator) Closure config) {
        def configurator = new ProjectConfigurator(executer, projectName)
        config.delegate = configurator
        config.call(configurator)
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
            producerConfig = """
                output.convention(layout.buildDirectory.file(providers.systemProperty("\${project.name}FileName").orElse("\${project.name}.jar")))
                content.convention(providers.systemProperty("\${project.name}Content").orElse(project.name))
            """.stripIndent()
            producerConfigOverrides = """
                layout.buildDirectory.convention(layout.projectDirectory.dir(providers.systemProperty("\${project.name}OutputDir").orElse("build")))
            """.stripIndent()
        }

        /**
         * Specifies that each project produce a single JAR as output.
         */
        void produceJars() {
            producerTaskClassName = "JarProducer"
            producerConfig = """
                output.convention(layout.buildDirectory.file(providers.systemProperty("\${project.name}FileName").orElse("\${project.name}.jar")))
                content.convention(providers.systemProperty("\${project.name}Content").orElse(project.name))
                timestamp.convention(providers.systemProperty("\${project.name}Timestamp").map { Long.parseLong(it) }.orElse(123L))
                entryName.convention(providers.systemProperty("\${project.name}EntryName").orElse("thing.class"))
            """.stripIndent()
            producerConfigOverrides = """
                layout.buildDirectory.convention(layout.projectDirectory.dir(providers.systemProperty("\${project.name}OutputDir").orElse("build")))
                tasks.withType(JarProducer) {
                    if (providers.systemProperty("\${project.name}ProduceNothing").present) {
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
         * ${project.name}FileName - changes the name of the file to write to in the directory
         * ${project.name}Content - changes the text to write to the output file.
         */
        void produceDirs() {
            producerTaskClassName = "DirProducer"
            producerConfig = """
                output.convention(layout.buildDirectory.dir(providers.systemProperty("\${project.name}DirName").orElse("\${project.name}-dir")))
                def defaultContent = project.name
                content.convention(providers.systemProperty("\${project.name}Content").orElse(defaultContent))
                def defaultNames = providers.systemProperty("\${project.name}EmptyDir").present ? [] : [project.name]
                names.convention(providers.systemProperty("\${project.name}FileName").map { [it] }.orElse(defaultNames))
            """.stripIndent()
            producerConfigOverrides = """
                layout.buildDirectory.convention(layout.projectDirectory.dir(providers.systemProperty("\${project.name}OutputDir").orElse("build")))
                tasks.withType(DirProducer) {
                    if (providers.systemProperty("\${project.name}ProduceNothing").present) {
                        content = ""
                    }
                    def namesProperty = providers.systemProperty("\${project.name}Names")
                    if (namesProperty.present) {
                        names.set(namesProperty.map { it.split(',') as List })
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
