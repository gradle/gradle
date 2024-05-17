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

import com.google.common.reflect.TypeToken
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.OptionValues
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.process.ExecOperations
import spock.lang.Issue

import java.util.concurrent.atomic.AtomicInteger

import static org.gradle.util.Matchers.matchesRegexp
import static org.hamcrest.Matchers.containsString

class ArtifactTransformValuesInjectionIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture, ValidationMessageChecker {

    def "transform can receive parameters, workspace and input artifact via abstract getter"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                        parameters {
                            extension = 'green'
                        }
                    }
                }
            }

            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Input
                    String getExtension()
                    void setExtension(String value)
                }

                @InputArtifact
                abstract Provider<FileSystemLocation> getInput()

                void transform(TransformOutputs outputs) {
                    File inputFile = input.get().asFile
                    println "processing \${inputFile.name}"
                    def output = outputs.file(inputFile.name + "." + parameters.extension)
                    output.text = "ok"
                }
            }
        """

        when:
        run(":a:resolve")

        then:
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "transform can receive parameter of type #type"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                prop = ${value}
                nested.nestedProp = ${value}
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            interface NestedType {
                @Input
                ${type} getNestedProp()
            }

            abstract class MakeGreen implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Input
                    ${type} getProp()
                    @Input @Optional
                    ${type} getOtherProp()
                    @Nested
                    NestedType getNested()
                }

                void transform(TransformOutputs outputs) {
                    println "processing using prop: \${parameters.prop.get()}, nested: \${parameters.nested.nestedProp.get()}"
                    assert parameters.otherProp.getOrNull() == ${expectedNullValue}
                }
            }
        """

        when:
        run("a:resolve")

        then:
        outputContains("processing using prop: ${expected}, nested: ${expected}")

        where:
        type                          | value          | expected     | expectedNullValue
        "Property<String>"            | "'value'"      | 'value'      | null
        "Property<Boolean>"           | "true"         | 'true'       | null
        "ListProperty<String>"        | "['a', 'b']"   | "[a, b]"     | "[]"
        "SetProperty<String>"         | "['a', 'b']"   | "[a, b]"     | "[] as Set"
        "MapProperty<String, Number>" | "[a: 1, b: 2]" | "[a:1, b:2]" | "[:]"
    }

    @Issue("https://github.com/gradle/gradle/issues/16982")
    def "transform can set convention on parameter of type #type"() {
        given:
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform()

        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<Parameters> {

                abstract static class Parameters extends TransformParameters {

                    { prop.convention($value) }

                    @Input
                    abstract ${type} getProp()
                }

                void transform(TransformOutputs outputs) {
                    println "processing using prop: \${parameters.prop.get()}"
                }
            }
        """

        when:
        run("a:resolve")

        then:
        outputContains("processing using prop: ${expected}")

        where:
        type                          | value            | expected
        "Property<Byte>"              | '42.byteValue()' | '42'
        "Property<Boolean>"           | 'true'           | 'true'
        "Property<String>"            | "'value'"        | 'value'
        "ListProperty<String>"        | "['a', 'b']"     | "[a, b]"
        "SetProperty<String>"         | "['a', 'b']"     | "[a, b]"
        "MapProperty<String, Number>" | "[a: 1, b: 2]"   | "[a:1, b:2]"
    }

    def "transform can receive a build service as a parameter"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        buildFile << """
            import ${BuildServiceParameters.name}
            import ${AtomicInteger.name}

            abstract class CountingService implements BuildService<BuildServiceParameters.None> {
                private final value = new AtomicInteger()

                int increment() {
                    def value = value.incrementAndGet()
                    println("service: value is \${value}")
                    return value
                }
            }

            def countingService = gradle.sharedServices.registerIfAbsent("counting", CountingService) {
            }
        """
        setupBuildWithColorTransform {
            params("""
                service = countingService
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            abstract class MakeGreen implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Internal
                    Property<CountingService> getService()
                }

                void transform(TransformOutputs outputs) {
                    def n = parameters.service.get().increment()
                    outputs.file("out-\${n}.txt").text = n
                }
            }
        """

        when:
        run(":a:resolve")

        then:
        outputContains("service: value is 1")
        outputContains("result = [out-1.txt]")
    }

    def "transform can receive Gradle provided service #serviceType via injection"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                private final ${serviceType} service

                @Inject
                MakeGreen(${serviceType} service) {
                    this.service = service
                }

                void transform(TransformOutputs outputs) {
                    assert service != null
                    println("received service")
                }
            }
        """

        when:
        run(":a:resolve")

        then:
        output.count("received service") == 1

        where:
        serviceType << [
            ObjectFactory,
            ProviderFactory,
            FileSystemOperations,
            ExecOperations,
        ].collect { it.name }
    }

    def "transform cannot receive Gradle provided service #serviceType via injection"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @Inject
                abstract ${serviceType} getService()

                void transform(TransformOutputs outputs) {
                    service
                    throw new RuntimeException()
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Failed to transform b.jar (project :b) to match attributes {artifactType=jar, color=green}.")
        failure.assertHasCause("No service of type interface ${serviceType} available.")

        where:
        serviceType << [
            ProjectLayout, // not isolated
            Instantiator, // internal
        ].collect { it.name }
    }

    def "transform parameters are validated for input output annotations"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        buildFile << """
            interface NestedType {
                @InputFile
                RegularFileProperty getInputFile()
                @OutputDirectory
                DirectoryProperty getOutputDirectory()
                Property<String> getStringProperty()
            }
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
                nested.inputFile = file("some")
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            @CacheableTransform
            abstract class MakeGreen implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    String getExtension()
                    void setExtension(String value)

                    @OutputDirectory
                    File getOutputDir()
                    void setOutputDir(File outputDir)

                    @Input
                    String getMissingInput()
                    void setMissingInput(String missing)

                    @Input
                    File getFileInput()
                    void setFileInput(File file)

                    @InputFiles
                    ConfigurableFileCollection getNoPathSensitivity()

                    @InputFile
                    File getNoPathSensitivityFile()
                    void setNoPathSensitivityFile(File file)

                    @InputDirectory
                    File getNoPathSensitivityDir()
                    void setNoPathSensitivityDir(File file)

                    @PathSensitive(PathSensitivity.ABSOLUTE)
                    @InputFiles
                    ConfigurableFileCollection getAbsolutePathSensitivity()

                    @Incremental
                    @Input
                    String getIncrementalNonFileInput()
                    void setIncrementalNonFileInput(String value)

                    @Nested
                    NestedType getNested()
                }

                void transform(TransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':a:resolver'.")
        failure.assertHasCause("Failed to transform b.jar (project :b) to match attributes {artifactType=jar, color=green}.")
        failure.assertThatCause(matchesRegexp('Could not isolate parameters MakeGreen\\$Parameters_Decorated@.* of artifact transform MakeGreen'))
        failure.assertHasCause('Some problems were found with the configuration of the artifact transform parameter MakeGreen.Parameters.')
        assertPropertyValidationErrors(
            absolutePathSensitivity: invalidUseOfAbsoluteSensitivity { includeLink().noIntro() },
            extension: missingAnnotationMessage { property('extension').missingInput().includeLink().noIntro() },
            fileInput: [
                missingValueMessage { property('fileInput').includeLink().noIntro() },
                incorrectUseOfInputAnnotation { property('fileInput').propertyType('File').includeLink().noIntro() },
            ],
            incrementalNonFileInput: [
                missingValueMessage { property('incrementalNonFileInput').includeLink().noIntro() },
                incompatibleAnnotations { property('incrementalNonFileInput').annotatedWith('Incremental').incompatibleWith('Input').includeLink().noIntro() },
            ],
            missingInput: missingValueMessage { property('missingInput').includeLink().noIntro() },
            'nested.outputDirectory': annotationInvalidInContext { annotation('OutputDirectory').includeLink() },
            'nested.inputFile': missingNormalizationStrategy { annotatedWith('InputFile').includeLink().noIntro() },
            'nested.stringProperty': missingAnnotationMessage { property('nested.stringProperty').missingInput().includeLink().noIntro() },
            noPathSensitivity: missingNormalizationStrategy { annotatedWith('InputFiles').includeLink().noIntro() },
            noPathSensitivityDir: missingNormalizationStrategy { annotatedWith('InputDirectory').includeLink().noIntro() },
            noPathSensitivityFile: missingNormalizationStrategy { annotatedWith('InputFile').includeLink().noIntro() },
            outputDir: annotationInvalidInContext { annotation('OutputDirectory').includeLink() }
        )
    }

    def "cannot query parameters for transform without parameters"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    println getParameters()
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertResolutionFailure(':a:resolver')
        failure.assertHasCause("Cannot query the parameters of an instance of TransformAction that takes no parameters.")
    }

    def "transform parameters type cannot use caching annotations"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            abstract class MakeGreen implements TransformAction<Parameters> {
                @CacheableTask @CacheableTransform
                interface Parameters extends TransformParameters {
                    @Input
                    String getExtension()
                    void setExtension(String value)
                }

                void transform(TransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':a:resolver'.")
        failure.assertHasCause("Failed to transform b.jar (project :b) to match attributes {artifactType=jar, color=green}.")
        failure.assertThatCause(matchesRegexp('Could not isolate parameters MakeGreen\\$Parameters_Decorated@.* of artifact transform MakeGreen'))
        failure.assertHasCause('Some problems were found with the configuration of the artifact transform parameter MakeGreen.Parameters.')
        failure.assertHasCause(invalidUseOfCacheableAnnotation {
            type('MakeGreen.Parameters').invalidAnnotation('CacheableTask').onlyMakesSenseOn('Task').includeLink()
        })
        failure.assertHasCause(invalidUseOfCacheableAnnotation {
            type('MakeGreen.Parameters').invalidAnnotation('CacheableTransform').onlyMakesSenseOn('TransformAction').includeLink()
        })
    }

    def "transform parameters type cannot use annotation @#ann.simpleName"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            abstract class MakeGreen implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Input
                    String getExtension()
                    void setExtension(String value)
                    @${ann.simpleName}
                    String getBad()
                    void setBad(String value)
                }

                void transform(TransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':a:resolver'.")
        failure.assertHasCause("Failed to transform b.jar (project :b) to match attributes {artifactType=jar, color=green}.")
        failure.assertThatCause(matchesRegexp('Could not isolate parameters MakeGreen\\$Parameters_Decorated@.* of artifact transform MakeGreen'))
        failure.assertHasCause('A problem was found with the configuration of the artifact transform parameter MakeGreen.Parameters.')
        assertPropertyValidationErrors(bad: annotationInvalidInContext { annotation(ann.simpleName) })

        where:
        ann << [OutputFile, OutputFiles, OutputDirectory, OutputDirectories, Destroys, LocalState, OptionValues]
    }

    def "transform parameters type cannot use injection annotation @#annotation.simpleName"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    String getExtension()
                    void setExtension(String value)
                    @${annotation.simpleName}
                    String getBad()
                    void setBad(String value)
                }

                void transform(TransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription('A problem occurred evaluating root project')
        failure.assertHasCause('Could not create an instance of type MakeGreen$Parameters.')
        failure.assertHasCause('Could not generate a decorated class for type MakeGreen.Parameters.')
        failure.assertHasCause("Cannot use @${annotation.simpleName} annotation on method Parameters.getBad().")

        where:
        annotation << [InputArtifact, InputArtifactDependencies]
    }

    def "transform action is validated for input output annotations"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            @CacheableTransform
            abstract class MakeGreen implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Input
                    String getExtension()
                    void setExtension(String value)
                }

                @InputFile
                File inputFile

                File notAnnotated

                @InputArtifact
                abstract Provider<FileSystemLocation> getNoPathSensitivity()

                @PathSensitive(PathSensitivity.ABSOLUTE)
                @InputArtifactDependencies
                abstract FileCollection getAbsolutePathSensitivityDependencies()

                @PathSensitive(PathSensitivity.NAME_ONLY)
                @InputFile @InputArtifact @InputArtifactDependencies
                Provider<FileSystemLocation> getConflictingAnnotations() { }

                void transform(TransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription('A problem occurred evaluating root project')
        failure.assertHasCause('Some problems were found with the configuration of MakeGreen.')
        String conflictingAnnotationsMessage = conflictingAnnotationsMessage {
            inConflict('InputFile', 'InputArtifact', 'InputArtifactDependencies')
        }
        assertPropertyValidationErrors(
            absolutePathSensitivityDependencies: invalidUseOfAbsoluteSensitivity { includeLink().noIntro() },
            'conflictingAnnotations': [
                conflictingAnnotationsMessage,
                annotationInvalidInContext { annotation('InputFile').forTransformAction() }
            ],
            inputFile: annotationInvalidInContext { annotation('InputFile').forTransformAction() },
            noPathSensitivity: missingNormalizationStrategy { annotatedWith('InputArtifact').noIntro() },
            notAnnotated: missingAnnotationMessage { property('extension').missingInput().includeLink().noIntro() },
        )
    }

    def "transform action type cannot use @#ann.simpleName"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            @${ann.simpleName}
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription('A problem occurred evaluating root project')
        failure.assertHasCause('A problem was found with the configuration of MakeGreen.')
        failure.assertHasCause(invalidUseOfCacheableAnnotation {
            type('MakeGreen').invalidAnnotation(ann.simpleName).onlyMakesSenseOn('Task').includeLink()
        })

        where:
        ann << [CacheableTask, UntrackedTask]
    }

    def "transform action type cannot use annotation @#ann.simpleName"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Input
                    String getExtension()
                    void setExtension(String value)
                }

                @${ann.simpleName}
                String getBad() { }

                void transform(TransformOutputs outputs) {
                    throw new RuntimeException()
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription('A problem occurred evaluating root project')
        failure.assertHasCause('A problem was found with the configuration of MakeGreen.')
        assertPropertyValidationErrors(bad: annotationInvalidInContext { annotation(ann.simpleName).forTransformAction() })

        where:
        ann << [Input, InputFile, InputDirectory, OutputFile, OutputFiles, OutputDirectory, OutputDirectories, Destroys, LocalState, OptionValues, Console, Internal]
    }

    def "transform can receive dependencies via abstract getter of type #targetType"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }
            project(':b') {
                dependencies {
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifactDependencies
                abstract ${targetType} getDependencies()
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "received dependencies files \${dependencies*.name} for processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = "ok"
                }
            }
        """

        when:
        run(":a:resolve")

        then:
        outputContains("received dependencies files [] for processing c.jar")
        outputContains("received dependencies files [c.jar] for processing b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        where:
        targetType << ["FileCollection", "Iterable<File>"]
    }

    def "transform can receive parameter object via constructor parameter"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform {
            params("""
                extension = 'green'
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Input
                    String getExtension()
                    void setExtension(String value)
                }

                private Parameters conf

                @Inject
                MakeGreen(Parameters conf) {
                    this.conf = conf
                }

                void transform(TransformOutputs outputs) {
                }
            }
        """

        expect:
        succeeds(":a:resolve")
    }

    def "transform cannot use @InputArtifact to receive #propertyType"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransform()
        def typeName = propertyType instanceof Class ? propertyType.name : propertyType.toString()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract ${typeName} getInput()

                void transform(TransformOutputs outputs) {
                    input
                    throw new RuntimeException("broken")
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription("A problem occurred evaluating root project")
        failure.assertHasCause("Could not register artifact transform MakeGreen (from {color=blue} to {color=green})")
        failure.assertHasCause("Cannot use @InputArtifact annotation on property MakeGreen.getInput() of type ${typeName}. Allowed property types: org.gradle.api.provider.Provider<org.gradle.api.file.FileSystemLocation>.")

        where:
        propertyType << [
            File,
            FileCollection,
            new TypeToken<Provider<File>>() {}.getType(),
            new TypeToken<Provider<String>>() {}.getType()
        ]
    }

    def "transform cannot use @InputArtifactDependencies to receive #propertyType"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @${annotation.name}
                abstract ${propertyType.name} getDependencies()

                void transform(TransformOutputs outputs) {
                    dependencies
                    throw new RuntimeException("broken")
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        failure.assertHasDescription("A problem occurred evaluating root project")
        failure.assertHasCause("Could not register artifact transform MakeGreen (from {color=blue} to {color=green})")
        failure.assertHasCause("Cannot use @InputArtifactDependencies annotation on property MakeGreen.getDependencies() of type ${propertyType.name}. Allowed property types: org.gradle.api.file.FileCollection.")

        where:
        annotation                | propertyType
        InputArtifactDependencies | File
        InputArtifactDependencies | String
    }

    def "transform cannot use @Inject to receive input file"() {
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @Inject
                abstract File getWorkspace()

                void transform(TransformOutputs outputs) {
                    workspace
                    throw new RuntimeException("broken")
                }
            }
        """

        when:
        fails(":a:resolve")

        then:
        // Documents existing behaviour. Should fail eagerly and with a better error message
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Execution failed for MakeGreen: ${file('b/build/b.jar')}.")
        failure.assertHasCause("No service of type class ${File.name} available.")
    }

    def "task implementation cannot use cacheable transform annotation"() {
        expectReindentedValidationMessage()
        buildFile << """
            @CacheableTransform
            class MyTask extends DefaultTask {
                @TaskAction void execute() {}
            }

            tasks.create('broken', MyTask)
        """

        expect:
        fails('broken')
        failure.assertHasDescription("A problem was found with the configuration of task ':broken' (type 'MyTask').")
        failure.assertThatDescription(containsString(invalidUseOfCacheableAnnotation {
            type('MyTask').invalidAnnotation('CacheableTransform').onlyMakesSenseOn('TransformAction').includeLink()
        }))
    }

    def "task @Nested bean cannot use cacheable annotations"() {
        expectReindentedValidationMessage()
        buildFile << """
            class MyTask extends DefaultTask {
                @Nested
                Options getThing() { new Options() }

                @TaskAction
                void go() { }
            }

            @CacheableTransform @CacheableTask
            class Options {
            }

            tasks.create('broken', MyTask)
        """

        expect:
        // Probably should be eager
        fails('broken')
        failure.assertHasDescription("Some problems were found with the configuration of task ':broken' (type 'MyTask').")
        failure.assertThatDescription(containsString(invalidUseOfCacheableAnnotation {
            type('Options').invalidAnnotation('CacheableTask').onlyMakesSenseOn('Task').includeLink()
        }))
        failure.assertThatDescription(containsString(invalidUseOfCacheableAnnotation {
            invalidAnnotation('CacheableTransform').onlyMakesSenseOn('TransformAction').includeLink()
        }))
    }

    def "task implementation cannot use injection annotation @#annotation.simpleName"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @${annotation.name}
                File getThing() { null }
            }

            tasks.create('broken', MyTask)
        """

        expect:
        fails('broken')
        failure.assertHasDescription("A problem occurred evaluating root project")
        failure.assertHasCause("Could not create task of type 'MyTask'.")
        failure.assertHasCause("Could not generate a decorated class for type MyTask.")
        failure.assertHasCause("Cannot use @${annotation.simpleName} annotation on method MyTask.getThing().")

        where:
        annotation << [InputArtifact, InputArtifactDependencies]
    }

    void assertPropertyValidationErrors(Map<String, Object> validationErrors) {
        int count = 0
        validationErrors.each { propertyName, errorMessageOrMessages ->
            def errorMessages = errorMessageOrMessages instanceof Iterable ? [*errorMessageOrMessages] : [errorMessageOrMessages]
            errorMessages.each { errorMessage ->
                count++
                System.err.println("Verifying assertion for $propertyName")
                failure.assertHasCause("Property '${propertyName}' ${errorMessage}")
            }
        }
        assert errorOutput.count("> Property") == count
    }

}
