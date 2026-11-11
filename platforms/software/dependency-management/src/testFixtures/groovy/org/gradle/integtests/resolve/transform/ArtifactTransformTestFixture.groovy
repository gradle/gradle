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

@SelfType(AbstractIntegrationSpec)
trait ArtifactTransformTestFixture extends VariantAwareDependencyResolutionTestFixture {
    abstract TestFile getBuildFile()

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
    void setupBuildWithColorAttributes(TestFile buildFile = getBuildFile(), @DelegatesTo(Builder) Closure cl = {}) {
        def builder = new Builder()
        builder.produceFiles()
        cl.delegate = builder
        cl.call()
        setupBuildWithColorAttributes(buildFile, builder)
    }

    void setupBuildWithColorAttributes(TestFile buildFile = getBuildFile(), Builder builder) {
        if (transformsInBuildSrc) {
            setupBuildSrcWithColorAttributes(buildFile, builder)
            return
        }
        buildFile << """
import ${javax.inject.Inject.name}
// TODO: Default imports should work for of inner classes
import ${org.gradle.api.artifacts.transform.TransformParameters.name}

${colorVariants()}
allprojects {
    task producer(type: ${builder.producerTaskClassName}) {
        ${builder.producerConfig}
    }
    afterEvaluate {
        ${builder.producerConfigOverrides}
    }
    artifacts {
        implementation tasks.producer.output
    }

    task resolve(type: ShowFileCollection) {
        def view = configurations.resolver.incoming.artifactView {
            attributes.attribute(color, 'green')
        }.files
        files.from(view)
    }
    task resolveArtifacts(type: ShowArtifactCollection) {
        collection = configurations.resolver.incoming.artifactView {
            attributes.attribute(color, 'green')
        }.artifacts
    }
}

import ${JarOutputStream.name}
import ${ZipEntry.name}

${jarProducerTask()}

${showFileCollectionTask()}
"""
        taskTypeWithOutputFileProperty(buildFile)
        taskTypeWithOutputDirectoryProperty(buildFile)
        taskTypeLogsArtifactCollectionDetails(buildFile)
    }

    private void setupBuildSrcWithColorAttributes(TestFile buildFile, Builder builder) {
        def taskTypes = buildSrcSourceFile(buildFile, "ColorVariantTaskTypes")
        taskTypes << jarProducerTask() << showFileCollectionTask()
        taskTypeWithOutputFileProperty(taskTypes)
        taskTypeWithOutputDirectoryProperty(taskTypes)
        taskTypeLogsArtifactCollectionDetails(taskTypes)
        configureEachProject(buildFile, """
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
        """, "color-variants")
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
     * Whether the fixture declares its classes in buildSrc and configures each project with plugins from buildSrc,
     * instead of declaring them in the root build script and configuring all projects from there with {@code allprojects}.
     * The former works with Isolated Projects. In that mode, the classes used by the build, such as transform actions,
     * are declared with {@link #buildSrcClasses(java.lang.String)} rather than in the root build script.
     * The color attribute is available to build scripts as the {@code color} extra property.
     */
    boolean isTransformsInBuildSrc() {
        false
    }

    /**
     * Declares the given classes in buildSrc of the build that has the given root build file, so that all its projects can use them.
     * Default imports of build scripts do not apply to buildSrc, so the commonly used packages are imported.
     */
    void buildSrcClasses(TestFile buildFile = getBuildFile(), String classes) {
        def dir = buildFile.parentFile.file("buildSrc/src/main/groovy")
        int index = (dir.listFiles()?.count { it.name.startsWith("TestClasses") } ?: 0) + 1
        buildSrcSourceFile(buildFile, "TestClasses${index}") << classes
    }

    /**
     * Applies the given configuration to each project of the build that has the given root build file.
     * The configuration is the body of a plugin in buildSrc with the given name.
     * The root build script applies the plugin, at the point where this method is called, as configuration in the root build script would
     * have been applied to the root project. The settings script applies the plugin to each other project, before its build script runs,
     * as {@code allprojects {}} in the root build script would have configured it.
     * It runs with the project as its delegate. As it is not a build script, it declares tasks with {@code tasks.create()} rather than {@code task name(type: T)}.
     */
    void configureEachProject(String configuration, String pluginName = "each-project") {
        configureEachProject(getBuildFile(), configuration, pluginName)
    }

    void configureEachProject(TestFile buildFile, String configuration, String pluginName = "each-project") {
        def pluginsDir = buildFile.parentFile.file("buildSrc/src/main/resources/META-INF/gradle-plugins")
        def pluginId = pluginName
        for (int index = 2; pluginsDir.file("${pluginId}.properties").exists(); index++) {
            pluginId = "${pluginName}-${index}"
        }
        def pluginClass = pluginId.split('-').collect { it.capitalize() }.join('') + "Plugin"
        buildSrcSourceFile(buildFile, pluginClass) << """
            class ${pluginClass} implements Plugin<Project> {
                void apply(Project project) {
                    project.with {
                        ${configuration}
                    }
                }
            }
        """
        pluginsDir.file("${pluginId}.properties") << "implementation-class=${pluginClass}"
        settingsFile(buildFile.parentFile.file("settings.gradle"), """
            gradle.lifecycle.beforeProject { project ->
                if (project.path != ':') {
                    project.plugins.apply('${pluginId}')
                }
            }
        """)
        buildFile << """
            apply plugin: '${pluginId}'
        """
    }

    private static TestFile buildSrcSourceFile(TestFile buildFile, String name) {
        def file = buildFile.parentFile.file("buildSrc/src/main/groovy/${name}.groovy")
        assert !file.exists()
        file << """
            import org.gradle.api.*
            import org.gradle.api.artifacts.*
            import org.gradle.api.artifacts.transform.*
            import org.gradle.api.attributes.*
            import org.gradle.api.file.*
            import org.gradle.api.model.*
            import org.gradle.api.provider.*
            import org.gradle.api.services.*
            import org.gradle.api.tasks.*
            import org.gradle.api.tasks.Optional
            import org.gradle.api.tasks.options.*
            import org.gradle.process.*
            import org.gradle.work.*
            import javax.inject.Inject
            import ${JarOutputStream.name}
            import ${ZipEntry.name}
        """
        file
    }

    /**
     * Adds the given configuration to each project, and declares the given classes.
     */
    private void addToAllProjects(TestFile buildFile, String configuration, String classes) {
        if (transformsInBuildSrc) {
            if (!configuration.trim().empty) {
                configureEachProject(buildFile, configuration, "color-transforms")
            }
            buildSrcClasses(buildFile, classes)
        } else {
            buildFile << """
                ${configuration.trim().empty ? "" : "allprojects {\n${configuration}\n}"}
                ${classes}
            """
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
        addToAllProjects(buildFile, """
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
        addToAllProjects(buildFile, """
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
    void setupBuildWithColorTransformImplementation(TestFile buildFile = getBuildFile(), boolean lenient = false) {
        setupBuildWithColorTransform(buildFile)
        addToAllProjects(buildFile, "", """
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
        setupBuildWithColorTransformImplementation(getBuildFile(), lenient)
    }

    /**
     * Each project produces a 'blue' variant, and has a `resolve` task that resolves the 'green' variant and a transform that converts 'blue' to 'green'.
     * By default the 'blue' variant will contain a single file, and the transform will produce a single 'green' file from this.
     */
    void setupBuildWithColorTransformThatTakesUpstreamArtifacts() {
        setupBuildWithColorTransform()
        addToAllProjects(buildFile, "", """
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
        addToAllProjects(buildFile, """
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
    void setupBuildWithColorTransform(TestFile buildFile, @DelegatesTo(TransformBuilder) Closure cl = {}) {
        def builder = new TransformBuilder()
        cl.delegate = builder
        cl.call()

        setupBuildWithColorAttributes(buildFile, builder)
        addToAllProjects(buildFile, """
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
        setupBuildWithColorTransform(buildFile, cl)
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
