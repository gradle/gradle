/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.Iterables
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.operations.dependencies.transforms.ExecuteTransformActionBuildOperationType
import org.gradle.operations.dependencies.transforms.IdentifyTransformExecutionProgressDetails
import org.gradle.operations.dependencies.transforms.SnapshotTransformInputsBuildOperationType
import org.gradle.operations.execution.ExecuteWorkBuildOperationType
import org.gradle.test.fixtures.file.TestFile

class ArtifactTransformExecutionBuildOperationIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture, DirectoryBuildCacheFixture {

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        requireOwnGradleUserHomeDir()

        // group name is included in the capabilities of components, which are part of the transform identity
        buildFile << """
            allprojects {
                apply plugin: 'base'
                group = "colored"
            }
        """

        settingsFile << """
            include 'producer', 'consumer'
        """
    }

    def setupExternalDependency(TestFile buildFile = getBuildFile()) {
        def m1 = mavenRepo.module("com.test", "test", "4.2").publish()
        m1.artifactFile.text = "com.test-test"

        buildFile << """
            allprojects {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }

                dependencies {
                    implementation 'com.test:test:4.2'
                }
            }
        """
    }

    def "transform executions are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformImplementation()
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")

                    artifactTypes {
                        jar {
                            attributes.attribute(color, 'blue')
                        }
                    }
                }
            }
        """

        when:
        withBuildCache().run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing [producer.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar.green]")

        def executionIdentifications = buildOperations.progress(IdentifyTransformExecutionProgressDetails)*.details
        executionIdentifications.size() == 2
        def projectTransformIdentification = executionIdentifications.find { it.artifactName == 'producer.jar' }
        def externalTransformIdentification = executionIdentifications.find { it.artifactName == 'test-4.2.jar' }

        [projectTransformIdentification, externalTransformIdentification].each {
            with(it) {
                identity != null
                fromAttributes == [color: 'blue']
                toAttributes == [color: 'green']
                transformActionClass == 'MakeGreen'
                secondaryInputValueHashBytes != null
            }
        }
        with(projectTransformIdentification.componentId) {
            buildPath == ':'
            projectPath == ':producer'
        }
        with(externalTransformIdentification.componentId) {
            group == 'com.test'
            module == 'test'
            version == '4.2'
        }

        List<BuildOperationRecord> executions = getTransformExecutions()
        executions.size() == 2
        def projectExecution = executions.find { it.details.identity == projectTransformIdentification.identity }
        def externalExecution = executions.find { it.details.identity == externalTransformIdentification.identity }
        [projectExecution, externalExecution].each { with(it) {
            details.workType == 'TRANSFORM'
        }}

        with(projectExecution.result) {
            skipMessage == null
            originBuildInvocationId == null
            executionReasons == ['No history is available.']
            cachingDisabledReasonMessage == 'Caching not enabled.'
            cachingDisabledReasonCategory == 'NOT_CACHEABLE'
        }
        projectExecution.failure == null
        with(snapshotInputsOperation(projectExecution).result) {
            hash != null
            classLoaderHash != null
            implementationClassName == 'MakeGreen'
            inputValueHashes.keySet() ==~ ['inputArtifactPath', 'inputPropertiesHash']
            outputPropertyNames == ['outputDirectory', 'resultsFile']
            inputFileProperties.keySet() ==~ ['inputArtifact', 'inputArtifactDependencies']
            with(inputFileProperties.inputArtifactDependencies) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_ABSOLUTE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
                roots == []
            }
            with(inputFileProperties.inputArtifact) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_ABSOLUTE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
                with(Iterables.getOnlyElement(roots)) {
                    hash != null
                    path.endsWith('producer.jar')
                }
            }
        }
        actionExecution(projectExecution) != null
        with(snapshotInputsOperation(externalExecution).result) {
            hash != null
            classLoaderHash != null
            implementationClassName == 'MakeGreen'
            inputValueHashes.keySet() ==~ ['inputArtifactPath', 'inputArtifactSnapshot', 'inputPropertiesHash']
            outputPropertyNames == ['outputDirectory', 'resultsFile']
            inputFileProperties.keySet() ==~ ['inputArtifact', 'inputArtifactDependencies']
            with(inputFileProperties.inputArtifactDependencies) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_ABSOLUTE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
                roots == []
            }
            with(inputFileProperties.inputArtifact) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_ABSOLUTE_PATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
                with(Iterables.getOnlyElement(roots)) {
                    hash != null
                    path.endsWith('test-4.2.jar')
                }
            }
        }
        actionExecution(externalExecution) != null

        when:
        withBuildCache().run ":consumer:resolve"
        executions = getTransformExecutions()
        executions.size() == 1
        projectExecution = executions.first()

        then:
        skipped(":producer:producer")

        with(projectExecution.result) {
            skipMessage == 'UP-TO-DATE'
            originExecutionTime > 0
            originBuildInvocationId != null
            executionReasons.empty
            cachingDisabledReasonMessage == 'Caching not enabled.'
            cachingDisabledReasonCategory == 'NOT_CACHEABLE'
        }
    }

    def "cacheability information for artifact transform executions is captured"() {
        setupBuildWithColorTransform()
        setupExternalDependency()
        buildFile << """
            @CacheableTransform
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.RELATIVE)
                @IgnoreEmptyDirectories
                @NormalizeLineEndings
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                // We need an incremental transform to have a project-bound workspace
                @Inject
                abstract InputChanges getInputChanges()

                @Classpath
                @InputArtifactDependencies
                abstract FileCollection getInputArtifactDependencies()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    assert input.file
                    def output = outputs.file(input.name + ".green")
                    if (input.file) {
                        output.text = input.text + ".green"
                    } else {
                        output.text = "missing.green"
                    }
                }
            }
        """

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")

                    artifactTypes {
                        jar {
                            attributes.attribute(color, 'blue')
                        }
                    }
                }
            }
        """
        def producerTransformSpec = { Map<String, Object> it -> it.artifactName == 'producer.jar' }

        when:
        withBuildCache().run ":consumer:resolve"
        then:
        def projectExecution = findTransformExecution(producerTransformSpec)
        with(snapshotInputsOperation(projectExecution).result) {
            hash != null
            classLoaderHash != null
            implementationClassName == 'MakeGreen'
            inputValueHashes.keySet() ==~ ['inputArtifactPath', 'inputPropertiesHash']
            outputPropertyNames == ['outputDirectory', 'resultsFile']
            inputFileProperties.keySet() ==~ ['inputArtifact', 'inputArtifactDependencies']
            with(inputFileProperties.inputArtifactDependencies) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_CLASSPATH', 'LINE_ENDING_SENSITIVITY_DEFAULT']
                with(Iterables.getOnlyElement(roots)) {
                    hash != null
                    path.endsWith('test-4.2.jar')
                }
            }
            with(inputFileProperties.inputArtifact) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_IGNORE_DIRECTORIES', 'FINGERPRINTING_STRATEGY_RELATIVE_PATH', 'LINE_ENDING_SENSITIVITY_NORMALIZE_LINE_ENDINGS']
                with(Iterables.getOnlyElement(roots)) {
                    hash != null
                    path.endsWith('producer.jar')
                }
            }
        }

        when:
        run 'clean'
        withBuildCache().run ':consumer:resolve'
        then:
        with(findTransformExecution(producerTransformSpec).result) {
            skipMessage == 'FROM-CACHE'
            originExecutionTime > 0
            originBuildInvocationId != null
            executionReasons.every { it ==~ 'Output property .* has been removed\\.'}
            cachingDisabledReasonMessage == null
            cachingDisabledReasonCategory == null
        }
    }

    def "captures all information on failure"() {
        setupBuildWithColorTransform()
        setupExternalDependency()
        buildFile << """
            @CacheableTransform
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.RELATIVE)
                @NormalizeLineEndings
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    throw new RuntimeException("BOOM")
                }
            }
        """
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")

                    artifactTypes {
                        jar {
                            attributes.attribute(color, 'blue')
                        }
                    }
                }
            }
        """

        when:
        withBuildCache().fails(":consumer:resolve")
        then:
        def projectExecution = findTransformExecution { it.artifactName == 'producer.jar' }
        projectExecution.failure == "java.lang.RuntimeException: BOOM"
        with(projectExecution.result) {
            skipMessage == null
            originExecutionTime == null
            originBuildInvocationId == null
            executionReasons == ['No history is available.']
            cachingDisabledReasonMessage == null
            cachingDisabledReasonCategory == null
        }
        with(snapshotInputsOperation(projectExecution).result) {
            hash != null
            classLoaderHash != null
            implementationClassName == 'MakeGreen'
            inputValueHashes.keySet() ==~ ['inputArtifactPath', 'inputPropertiesHash']
            outputPropertyNames == ['outputDirectory', 'resultsFile']
            inputFileProperties.keySet() ==~ ['inputArtifact', 'inputArtifactDependencies']
            with(inputFileProperties.inputArtifact) {
                hash != null
                attributes == ['DIRECTORY_SENSITIVITY_DEFAULT', 'FINGERPRINTING_STRATEGY_RELATIVE_PATH', 'LINE_ENDING_SENSITIVITY_NORMALIZE_LINE_ENDINGS']
                roots
            }
        }
        actionExecution(projectExecution) != null
    }

    def "classpath notation #classpathNotation transforms can be captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransform()
        buildFile << """
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    assert input.file
                    def output = outputs.file(input.name + ".green")
                    output.text = input.name + ".green"
                }
            }
        """

        // We need to add a Kotlin build file here so we have access to `gradleKotlinDsl()`
        file("consumer/build.gradle.kts") << """
            val color = Attribute.of("color", String::class.java)
            dependencies {
                implementation($classpathNotation)

                artifactTypes {
                    create("jar") {
                        attributes.attribute(color, "blue")
                    }
                }
            }
        """

        when:
        withBuildCache().run ":consumer:resolve"
        then:
        def executionIdentifications = buildOperations.progress(IdentifyTransformExecutionProgressDetails).details

        executionIdentifications.each { with(it) {
            with(componentId) {
                displayName == displayName
            }
        }}
        def inputArtifactNames = executionIdentifications.collect { it.artifactName }
        !inputArtifactNames.empty
        (inputArtifactNames as Set).size() == inputArtifactNames.size()

        where:
        classpathNotation   | displayName
        'gradleApi()'       | 'Gradle API'
        'gradleTestKit()'   | 'Gradle TestKit'
        'localGroovy()'     | 'Local Groovy'
        'gradleKotlinDsl()' | 'Gradle Kotlin DSL'
    }

    def "file collection transform can be captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformImplementation()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation files("file1.jar", "file2.jar")

                    artifactTypes {
                        jar {
                            attributes.attribute(color, 'blue')
                        }
                    }
                }
            }
        """
        (1..2).each {
            file("consumer/file${it}.jar").text = "file $it"
        }

        when:
        withBuildCache().run ":consumer:resolve"
        then:
        def executionIdentifications = buildOperations.progress(IdentifyTransformExecutionProgressDetails).details

        executionIdentifications.each { with(it) {
            artifactName == componentId.displayName
        }}
        executionIdentifications.artifactName ==~ ['file1.jar', 'file2.jar']
    }

    def "transform chains are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorAttributes()
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation files("file1.jar", "file2.jar")
                    implementation project(":producer")
                    implementation localGroovy()

                    artifactTypes {
                        jar {
                            attributes.attribute(color, 'blue')
                        }
                    }
                }
            }

            abstract class Multiplier implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    assert input.file
                    for (def i : 1..3) {
                        def output = outputs.file(input.name + i + ".red")
                        output.text = input.text + "-red-" + i
                    }
                }
            }

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

            allprojects {
                dependencies {
                   registerTransform(Multiplier) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'red')
                    }
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'red')
                        to.attribute(color, 'green')
                    }
                }
            }

        """
        (1..2).each {
            file("consumer/file${it}.jar").text = "file $it"
        }

        when:
        withBuildCache().run ":consumer:resolveArtifacts"
        then:
        def executionIdentifications = buildOperations.progress(IdentifyTransformExecutionProgressDetails).details

        def initialArtifactNames = executionIdentifications.findAll { it.fromAttributes == [color: 'blue'] }.artifactName
        def intermediateArtifactNames = executionIdentifications.findAll { it.fromAttributes == [color: 'red'] }.artifactName
        initialArtifactNames + intermediateArtifactNames ==~ executionIdentifications.artifactName
        intermediateArtifactNames ==~ initialArtifactNames.collectMany { (1..3).collect { idx -> "${it}${idx}.red".toString() }}

        def groupedIdentifications = executionIdentifications.groupBy {
            def componentId = it.componentId
            if (componentId.buildPath == ':' && componentId.projectPath == ':producer') {
                return 'project'
            }
            if (componentId.group == 'com.test' && componentId.module == 'test' && componentId.version == '4.2') {
                return 'module'
            }
            if (componentId.displayName == 'Local Groovy') {
                return 'classpathNotation'
            }
            if (componentId.displayName in ['file1.jar', 'file2.jar']) {
                return 'fileDependency'
            }
            return 'unspecified'
        }
        !groupedIdentifications.unspecified

        // Check the final component ids do not change within the chain.
        outputContains("""components = ${
            ((['file1.jar', 'file2.jar'] + (['Local Groovy'] * 14) + ['project :producer', 'com.test:test:4.2'])).collectMany {
                [it] * 3 // The multiplier creates three copies of everything.
            }
        }""")
    }

    BuildOperationRecord findTransformExecution(@ClosureParams(value = FromString, options =  ['Map<String,Object>']) Closure<Boolean> spec) {
        def transformIdentification = buildOperations.progress(IdentifyTransformExecutionProgressDetails)*.details.find(spec)
        return getTransformExecutions().find { it.details.identity == transformIdentification.identity }
    }

    BuildOperationRecord snapshotInputsOperation(BuildOperationRecord transformExecution) {
        Iterables.getOnlyElement(buildOperations.children(transformExecution, SnapshotTransformInputsBuildOperationType))
    }

    BuildOperationRecord actionExecution(BuildOperationRecord transformExecution) {
        def actionExecution = buildOperations.search(transformExecution, ExecuteTransformActionBuildOperationType)
        return actionExecution.empty ? null : Iterables.getOnlyElement(actionExecution)
    }

    List<BuildOperationRecord> getTransformExecutions() {
        buildOperations.all(ExecuteWorkBuildOperationType)
    }
}
