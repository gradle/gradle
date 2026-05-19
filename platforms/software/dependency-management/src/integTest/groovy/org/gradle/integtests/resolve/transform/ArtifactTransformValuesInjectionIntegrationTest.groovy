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

import org.gradle.api.problems.Severity
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
import org.gradle.process.ExecOperations
import spock.lang.Issue

import java.util.concurrent.atomic.AtomicInteger

import static org.gradle.util.Matchers.matchesRegexp

class ArtifactTransformValuesInjectionIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {

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

                abstract static class Parameters implements TransformParameters {

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
        failure.assertHasDescription("Execution failed for task ':a:resolve' (registered in build file 'build.gradle').")
        failure.assertHasCause("Failed to transform b.jar (project ':b') to match attributes {artifactType=jar, color=green}.")
        failure.assertHasCause("No service of type interface ${serviceType} available.")

        where:
        serviceType << [
            ProjectLayout, // not isolated
            Instantiator, // internal
        ].collect { it.name }
    }

    def "transform parameters are validated for input output annotations"() {
        enableProblemsApiCheck()
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
        failure.assertHasDescription("Execution failed for task ':a:resolve' (registered in build file 'build.gradle').")
        failure.assertHasCause("Could not resolve all files for configuration ':a:resolver'.")
        failure.assertHasCause("Failed to transform b.jar (project ':b') to match attributes {artifactType=jar, color=green}.")
        failure.assertThatCause(matchesRegexp('Could not isolate parameters MakeGreen\\$Parameters_Decorated@.* of artifact transform MakeGreen'))
        failure.assertHasCause('Some problems were found with the configuration of the artifact transform parameter MakeGreen.Parameters.')

        verifyAll(receivedProblem(0)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:annotation-invalid-in-context'
            definition.id.displayName == 'Invalid annotation in context'
            contextualLabel == "Property 'nested.outputDirectory' is annotated with invalid property type @OutputDirectory"
            details == "The '@OutputDirectory' annotation cannot be used in this context"
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Console, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @Nested, @ReplacedBy or @ServiceReference',
            ]
            additionalData.asMap == ['propertyName': 'outputDirectory', 'parentPropertyName': 'nested']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#annotation_invalid_in_context"
        }
        verifyAll(receivedProblem(1)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:annotation-invalid-in-context'
            definition.id.displayName == 'Invalid annotation in context'
            contextualLabel == "Property 'outputDir' is annotated with invalid property type @OutputDirectory"
            details == "The '@OutputDirectory' annotation cannot be used in this context"
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Console, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @Nested, @ReplacedBy or @ServiceReference',
            ]
            additionalData.asMap == ['propertyName': 'outputDir']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#annotation_invalid_in_context"
        }
        verifyAll(receivedProblem(2)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:cacheable-transform-cant-use-absolute-sensitivity'
            definition.id.displayName == 'Property declared to be sensitive to absolute paths'
            contextualLabel == "Property 'absolutePathSensitivity' is declared to be sensitive to absolute paths"
            details == 'This is not allowed for cacheable transforms'
            solutions == ['Use a different normalization strategy via @PathSensitive, @Classpath or @CompileClasspath']
            additionalData.asMap == ['propertyName': 'absolutePathSensitivity']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#cacheable_transform_cant_use_absolute_sensitivity"
        }
        verifyAll(receivedProblem(3)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:incompatible-annotations'
            definition.id.displayName == 'Incompatible annotations'
            contextualLabel == "Property 'incrementalNonFileInput' is annotated with @Incremental but that is not allowed for 'Input' properties"
            details == "This modifier is used in conjunction with a property of type 'Input' but this doesn't have semantics"
            solutions == ["Remove the '@Incremental' annotation"]
            additionalData.asMap == ['propertyName': 'incrementalNonFileInput']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#incompatible_annotations"
        }
        verifyAll(receivedProblem(4)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:incorrect-use-of-input-annotation'
            definition.id.displayName == 'Incorrect use of @Input annotation'
            contextualLabel == "Property 'fileInput' has @Input annotation used on property of type 'File'"
            details == "A property of type 'File' annotated with @Input cannot determine how to interpret the file"
            solutions == [
                'Annotate with @InputFile for regular files',
                'Annotate with @InputFiles for collections of files',
                'If you want to track the path, return File.absolutePath as a String and keep @Input',
            ]
            additionalData.asMap == ['propertyName': 'fileInput']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#incorrect_use_of_input_annotation"
        }
        verifyAll(receivedProblem(5)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:missing-annotation'
            definition.id.displayName == 'Missing annotation'
            contextualLabel == "Property 'extension' is missing an input annotation"
            details == 'Properties must be annotated so that Gradle knows how to handle them during up-to-date checking'
            solutions == ['Add an input annotation', 'Mark it as @Internal']
            additionalData.asMap == ['propertyName': 'extension']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#missing_annotation"
        }
        verifyAll(receivedProblem(6)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:missing-annotation'
            definition.id.displayName == 'Missing annotation'
            contextualLabel == "Property 'nested.stringProperty' is missing an input annotation"
            details == 'Properties must be annotated so that Gradle knows how to handle them during up-to-date checking'
            solutions == ['Add an input annotation', 'Mark it as @Internal']
            additionalData.asMap == ['propertyName': 'stringProperty', 'parentPropertyName': 'nested']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#missing_annotation"
        }
        verifyAll(receivedProblem(7)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:missing-normalization-annotation'
            definition.id.displayName == 'Missing normalization'
            contextualLabel == "Property 'nested.inputFile' is annotated with @InputFile but missing a normalization strategy"
            details == "If you don't declare the normalization, outputs can't be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly"
            solutions == ['Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath']
            additionalData.asMap == ['propertyName': 'inputFile', 'parentPropertyName': 'nested']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#missing_normalization_annotation"
        }
        verifyAll(receivedProblem(8)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:missing-normalization-annotation'
            definition.id.displayName == 'Missing normalization'
            contextualLabel == "Property 'noPathSensitivity' is annotated with @InputFiles but missing a normalization strategy"
            details == "If you don't declare the normalization, outputs can't be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly"
            solutions == ['Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath']
            additionalData.asMap == ['propertyName': 'noPathSensitivity']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#missing_normalization_annotation"
        }
        verifyAll(receivedProblem(9)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:missing-normalization-annotation'
            definition.id.displayName == 'Missing normalization'
            contextualLabel == "Property 'noPathSensitivityDir' is annotated with @InputDirectory but missing a normalization strategy"
            details == "If you don't declare the normalization, outputs can't be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly"
            solutions == ['Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath']
            additionalData.asMap == ['propertyName': 'noPathSensitivityDir']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#missing_normalization_annotation"
        }
        verifyAll(receivedProblem(10)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:missing-normalization-annotation'
            definition.id.displayName == 'Missing normalization'
            contextualLabel == "Property 'noPathSensitivityFile' is annotated with @InputFile but missing a normalization strategy"
            details == "If you don't declare the normalization, outputs can't be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly"
            solutions == ['Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath']
            additionalData.asMap == ['propertyName': 'noPathSensitivityFile']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#missing_normalization_annotation"
        }
        verifyAll(receivedProblem(11)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:value-not-set'
            definition.id.displayName == 'Value not set'
            contextualLabel == "Property 'fileInput' doesn't have a configured value"
            details == "This property isn't marked as optional and no value has been configured"
            solutions == ["Assign a value to 'fileInput'", "Mark property 'fileInput' as optional"]
            additionalData.asMap == ['propertyName': 'fileInput']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#value_not_set"
        }
        verifyAll(receivedProblem(12)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:value-not-set'
            definition.id.displayName == 'Value not set'
            contextualLabel == "Property 'incrementalNonFileInput' doesn't have a configured value"
            details == "This property isn't marked as optional and no value has been configured"
            solutions == ["Assign a value to 'incrementalNonFileInput'", "Mark property 'incrementalNonFileInput' as optional"]
            additionalData.asMap == ['propertyName': 'incrementalNonFileInput']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#value_not_set"
        }
        verifyAll(receivedProblem(13)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:value-not-set'
            definition.id.displayName == 'Value not set'
            contextualLabel == "Property 'missingInput' doesn't have a configured value"
            details == "This property isn't marked as optional and no value has been configured"
            solutions == ["Assign a value to 'missingInput'", "Mark property 'missingInput' as optional"]
            additionalData.asMap == ['propertyName': 'missingInput']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#value_not_set"
        }
    }

    def "can query parameters for transform with None parameters"() {
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
                    println("Parameters: " + getParameters())
                }
            }
        """

        expect:
        succeeds(":a:resolve")
        outputContains("Parameters: org.gradle.api.artifacts.transform.TransformParameters\$None@")
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
        failure.assertHasDescription("Execution failed for task ':a:resolve' (registered in build file 'build.gradle').")
        failure.assertHasCause("Could not resolve all files for configuration ':a:resolver'.")
        failure.assertHasCause("Failed to transform b.jar (project ':b') to match attributes {artifactType=jar, color=green}.")
        failure.assertThatCause(matchesRegexp('Could not isolate parameters MakeGreen\\$Parameters_Decorated@.* of artifact transform MakeGreen'))
        failure.assertHasCause('Some problems were found with the configuration of the artifact transform parameter MakeGreen.Parameters.')
        failure.assertHasErrorOutput("Type 'MakeGreen.Parameters' is incorrectly annotated with @CacheableTask")
        failure.assertHasErrorOutput("Type 'MakeGreen.Parameters' is incorrectly annotated with @CacheableTransform")
    }

    def "transform parameters type cannot use annotation @#ann.simpleName"() {
        enableProblemsApiCheck()
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
        failure.assertHasDescription("Execution failed for task ':a:resolve' (registered in build file 'build.gradle').")
        failure.assertHasCause("Could not resolve all files for configuration ':a:resolver'.")
        failure.assertHasCause("Failed to transform b.jar (project ':b') to match attributes {artifactType=jar, color=green}.")
        failure.assertThatCause(matchesRegexp('Could not isolate parameters MakeGreen\\$Parameters_Decorated@.* of artifact transform MakeGreen'))
        failure.assertHasCause('A problem was found with the configuration of the artifact transform parameter MakeGreen.Parameters.')
        verifyAll(receivedProblem) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:annotation-invalid-in-context'
            definition.id.displayName == 'Invalid annotation in context'
            contextualLabel == "Property 'bad' is annotated with invalid property type @${ann.simpleName}"
            details == "The '@${ann.simpleName}' annotation cannot be used in this context"
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Console, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @Nested, @ReplacedBy or @ServiceReference',
            ]
            additionalData.asMap == ['propertyName': 'bad']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#annotation_invalid_in_context"
        }

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
        failure.assertHasCause("Cannot use @${annotation.simpleName} annotation on method Parameters.getBad(): String.")

        where:
        annotation << [InputArtifact, InputArtifactDependencies]
    }

    def "transform action is validated for input output annotations"() {
        enableProblemsApiCheck()
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

        verifyAll(receivedProblem(0)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:annotation-invalid-in-context'
            definition.id.displayName == 'Invalid annotation in context'
            contextualLabel == "Property 'conflictingAnnotations' is annotated with invalid property type @InputFile"
            details == "The '@InputFile' annotation cannot be used in this context"
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Inject, @InputArtifact or @InputArtifactDependencies',
            ]
            additionalData.asMap == ['propertyName': 'conflictingAnnotations']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#annotation_invalid_in_context"
        }
        verifyAll(receivedProblem(1)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:annotation-invalid-in-context'
            definition.id.displayName == 'Invalid annotation in context'
            contextualLabel == "Property 'inputFile' is annotated with invalid property type @InputFile"
            details == "The '@InputFile' annotation cannot be used in this context"
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Inject, @InputArtifact or @InputArtifactDependencies',
            ]
            additionalData.asMap == ['propertyName': 'inputFile']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#annotation_invalid_in_context"
        }
        verifyAll(receivedProblem(2)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:cacheable-transform-cant-use-absolute-sensitivity'
            definition.id.displayName == 'Property declared to be sensitive to absolute paths'
            contextualLabel == "Property 'absolutePathSensitivityDependencies' is declared to be sensitive to absolute paths"
            details == 'This is not allowed for cacheable transforms'
            solutions == ['Use a different normalization strategy via @PathSensitive, @Classpath or @CompileClasspath']
            additionalData.asMap == ['propertyName': 'absolutePathSensitivityDependencies']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#cacheable_transform_cant_use_absolute_sensitivity"
        }
        verifyAll(receivedProblem(3)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:conflicting-annotations'
            definition.id.displayName == 'Type has conflicting annotation'
            contextualLabel == "Property 'conflictingAnnotations' has conflicting type annotations declared: @InputFile, @InputArtifact, @InputArtifactDependencies"
            details == 'The different annotations have different semantics and Gradle cannot determine which one to pick'
            solutions == ['Choose between one of the conflicting annotations']
            additionalData.asMap == ['propertyName': 'conflictingAnnotations']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#conflicting_annotations"
        }
        verifyAll(receivedProblem(4)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:missing-annotation'
            definition.id.displayName == 'Missing annotation'
            contextualLabel == "Property 'notAnnotated' is missing an input annotation"
            details == 'Properties must be annotated so that Gradle knows how to handle them during up-to-date checking'
            solutions == ['Add an input annotation', 'Mark it as @Internal']
            additionalData.asMap == ['propertyName': 'notAnnotated']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#missing_annotation"
        }
        verifyAll(receivedProblem(5)) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:missing-normalization-annotation'
            definition.id.displayName == 'Missing normalization'
            contextualLabel == "Property 'noPathSensitivity' is annotated with @InputArtifact but missing a normalization strategy"
            details == "If you don't declare the normalization, outputs can't be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly"
            solutions == ['Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath']
            additionalData.asMap == ['propertyName': 'noPathSensitivity']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#missing_normalization_annotation"
        }
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
        failure.assertHasErrorOutput("Type 'MakeGreen' is incorrectly annotated with @${ann.simpleName}")

        where:
        ann << [CacheableTask, UntrackedTask]
    }

    def "transform action type cannot use annotation @#ann.simpleName"() {
        enableProblemsApiCheck()
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
        verifyAll(receivedProblem) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:annotation-invalid-in-context'
            definition.id.displayName == 'Invalid annotation in context'
            contextualLabel == "Property 'bad' is annotated with invalid property type @${ann.simpleName}"
            details == "The '@${ann.simpleName}' annotation cannot be used in this context"
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Inject, @InputArtifact or @InputArtifactDependencies',
            ]
            additionalData.asMap == ['propertyName': 'bad']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#annotation_invalid_in_context"
        }

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
        failure.assertHasCause("Cannot use @InputArtifact annotation on property 'input' of type ${typeName}. Allowed property types: org.gradle.api.provider.Provider<org.gradle.api.file.FileSystemLocation>.")

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
        failure.assertHasCause("Cannot use @InputArtifactDependencies annotation on property 'dependencies' of type ${propertyType.name}. Allowed property types: org.gradle.api.file.FileCollection.")

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
        failure.assertHasDescription("Execution failed for task ':a:resolve' (registered in build file 'build.gradle').")
        failure.assertHasCause("Execution failed for MakeGreen: ${file('b/build/b.jar')}.")
        failure.assertHasCause("No service of type class ${File.name} available.")
    }

    def "task implementation cannot use cacheable transform annotation"() {
        enableProblemsApiCheck()
        buildFile << """
            @CacheableTransform
            class MyTask extends DefaultTask {
                @TaskAction void execute() {}
            }

            tasks.create('broken', MyTask)
        """

        when:
        fails('broken')

        then:
        failure.assertHasDescription("A problem was found with the configuration of task ':broken' (type 'MyTask').")
        verifyAll(receivedProblem) {
            severity == Severity.ERROR
            fqid == 'validation:type-validation:invalid-use-of-type-annotation'
            definition.id.displayName == 'Incorrect use of type annotation'
            contextualLabel == "Type 'MyTask' is incorrectly annotated with @CacheableTransform"
            details == 'This annotation only makes sense on TransformAction types'
            solutions == ['Remove the annotation']
            additionalData.asMap == ['typeName': 'MyTask']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#invalid_use_of_cacheable_annotation"
        }
    }

    def "task @Nested bean cannot use cacheable annotations"() {
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
        enableProblemsApiCheck()

        when:
        // Probably should be eager
        fails('broken')

        then:
        failure.assertHasDescription("Some problems were found with the configuration of task ':broken' (type 'MyTask').")
        verifyAll(receivedProblem(0)) {
            severity == Severity.ERROR
            fqid == 'validation:type-validation:invalid-use-of-type-annotation'
            definition.id.displayName == 'Incorrect use of type annotation'
            contextualLabel == "Type 'Options' is incorrectly annotated with @CacheableTask"
            details == 'This annotation only makes sense on Task types'
            solutions == ['Remove the annotation']
            additionalData.asMap == ['typeName': 'Options']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#invalid_use_of_cacheable_annotation"
        }
        verifyAll(receivedProblem(1)) {
            severity == Severity.ERROR
            fqid == 'validation:type-validation:invalid-use-of-type-annotation'
            definition.id.displayName == 'Incorrect use of type annotation'
            contextualLabel == "Type 'Options' is incorrectly annotated with @CacheableTransform"
            details == 'This annotation only makes sense on TransformAction types'
            solutions == ['Remove the annotation']
            additionalData.asMap == ['typeName': 'Options']
            definition.documentationLink.url == "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#invalid_use_of_cacheable_annotation"
        }
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
        failure.assertHasCause("Cannot use @${annotation.simpleName} annotation on method MyTask.getThing(): File.")

        where:
        annotation << [InputArtifact, InputArtifactDependencies]
    }

}
