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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationNotificationsFixture
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType

class CalculateTaskGraphBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    final buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @SuppressWarnings("GroovyUnusedDeclaration")
    final operationNotificationsFixture = new BuildOperationNotificationsFixture(executer, temporaryFolder)

    def "requested and filtered tasks are exposed"() {
        settingsFile << """
            include "a"
            include "b"
            include "a:c"
        """

        buildFile << """
            allprojects {
                task otherTask
                task someTask
                someTask.dependsOn otherTask
            }
        """
        when:
        succeeds('help')

        then:
        operation().result.requestedTaskPaths == [":help"]
        operation().result.excludedTaskPaths == []

        when:
        succeeds('someTask')

        then:
        operation().result.requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        operation().result.excludedTaskPaths == []

        when:
        succeeds('someTask', '-x', ':b:someTask')

        then:
        operation().result.requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        operation().result.excludedTaskPaths == [":b:someTask"]

        when:
        succeeds('someTask', '-x', 'otherTask')

        then:
        operation().result.requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        operation().result.excludedTaskPaths == [":a:c:otherTask", ":a:otherTask", ":b:otherTask", ":otherTask"]

        when:
        succeeds(':a:someTask')

        then:
        operation().result.requestedTaskPaths == [":a:someTask"]
        operation().result.excludedTaskPaths == []
    }

    def "task plan is exposed"() {
        settingsFile << """
            include "a"
            include "b"
            include "a:c"
        """

        buildFile << """
            allprojects {
                task otherTask
                task someTask
                someTask.dependsOn otherTask
            }
        """
        when:
        succeeds('someTask')

        then:
        operation().result.taskPlan.task.taskPath == [':otherTask', ':someTask', ':a:otherTask', ':a:someTask', ':b:otherTask', ':b:someTask', ':a:c:otherTask', ':a:c:someTask']
    }

    def "errors in calculating task graph are exposed"() {
        when:
        fails('someNonexistent')

        then:
        operation().failure.contains("Task 'someNonexistent' not found in root project")
    }

    def "build path for calculated task graph is exposed"() {
        settingsFile << """
            includeBuild "b"
        """

        file("buildSrc/settings.gradle").createFile()

        buildFile << """
            apply plugin:'java'

            dependencies {
                implementation "org.acme:b:1.0"
            }
        """

        file("b/build.gradle") << """
            apply plugin:'java-library'
            group = 'org.acme'
            version = '1.0'
        """
        file('b/settings.gradle') << ""

        when:
        succeeds('build')

        def taskGraphCalculations = buildOperations.all(CalculateTaskGraphBuildOperationType)

        then:
        taskGraphCalculations.size() == 3
        taskGraphCalculations[0].details.buildPath == ":buildSrc"
        taskGraphCalculations[0].result.requestedTaskPaths == [":jar"]
        taskGraphCalculations[1].details.buildPath == ":"
        taskGraphCalculations[1].result.requestedTaskPaths == [":build"]
        taskGraphCalculations[2].details.buildPath == ":b"
        taskGraphCalculations[2].result.requestedTaskPaths == [":compileJava", ":jar"]
    }

    def "exposes task plan details"() {
        file("included-build").mkdir()
        file("included-build/settings.gradle")
        file("included-build/build.gradle") << """
            apply plugin:'java-library'
            group = 'org.acme'
            version = '1.0'
        """

        file('src/main/java/org/acme/Library.java') << """
            package org.acme;

            class Library {
            }
        """
        settingsFile << """
            includeBuild 'included-build'
        """

        buildFile << """
            apply plugin:'java-library'

            dependencies {
                implementation 'org.acme:included-build:1.0'
            }
            task independentTask
            task otherTask
            task anotherTask
            task firstTask
            task secondTask
            task lastTask
            task someTask

            someTask.dependsOn anotherTask
            someTask.dependsOn otherTask
            someTask.mustRunAfter firstTask
            someTask.shouldRunAfter secondTask
            someTask.finalizedBy lastTask
        """
        when:
        succeeds('classes', 'independentTask', 'someTask')

        then:
        def operations = this.operations()
        operations.size() == 2
        with(operations[0].result.taskPlan) {
            task.taskPath == [":compileJava", ":processResources", ":classes", ":independentTask", ":anotherTask", ":otherTask", ":someTask", ":lastTask"]
            task.buildPath == [":", ":", ":", ":", ":", ":", ":", ":"]
            dependencies.taskPath.collect { it.sort() } == [[":compileJava"], [], [":compileJava", ":processResources"], [], [], [], [":anotherTask", ":otherTask"], []]
            dependencies.buildPath == [[":included-build"], [], [":", ":"], [], [], [], [":", ":"], []]
            finalizedBy.taskPath == [[], [], [], [], [], [], [":lastTask"], []]
            mustRunAfter.taskPath == [[], [], [], [], [], [], [":firstTask"], []]
            shouldRunAfter.taskPath == [[], [], [], [], [], [], [":secondTask"], []]
        }
        with(operations[1].result.taskPlan) {
            task.taskPath == [':compileJava']
            task.buildPath == [':included-build']
        }
    }

    def "exposes plan details with nested artifact transforms"() {
        file('producer/src/main/java/artifact/transform/sample/producer/Producer.java') << """
            package artifact.transform.sample.producer;

            public final class Producer {

                public String sayHello(String name) {
                    return "Hello, " + name + "!";
                }
            }
        """
        file('producer/build.gradle') << """
            apply plugin:'java-library'
        """

        buildFile << """
            import org.gradle.api.artifacts.transform.TransformParameters
            import org.gradle.api.artifacts.transform.TransformAction

            plugins {
                id 'java'
                id 'application'
            }

            def artifactType = Attribute.of('artifactType', String)
            def minified = Attribute.of('minified', Boolean)
            def optimized = Attribute.of('optimized', Boolean)
            dependencies {
                attributesSchema {
                    attribute(minified)
                    attribute(optimized)
                }
                artifactTypes.getByName("jar") {
                    attributes.attribute(minified, false)
                }
                artifactTypes.getByName("jar") {
                    attributes.attribute(optimized, false)
                }
            }

            configurations.all {
                afterEvaluate {
                    if (canBeResolved) {
                        attributes.attribute(minified, true)
                        attributes.attribute(optimized, true)
                    }
                }
            }

            dependencies {
                registerTransform(SomeTransform) {
                    from.attribute(minified, false).attribute(artifactType, "jar")
                    to.attribute(minified, true).attribute(artifactType, "jar")
                }
                registerTransform(SomeTransform) {
                    from.attribute(optimized, false).attribute(minified, true).attribute(artifactType, "jar")
                    to.attribute(optimized, true).attribute(minified, true).attribute(artifactType, "jar")
                }
            }

            dependencies {
                implementation project(':producer')
            }

            application {
                // Define the main class for the application.
                mainClass = 'artifact.transform.sample.App'
            }

            abstract class SomeTransform implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Override
                void transform(TransformOutputs outputs) {
                    println "Transforming printed to System.out"
                    outputs.file(inputArtifact)
                }
            }
        """
        file('src/main/java/artifact/transform/sample/App.java') << """
            package artifact.transform.sample;

            import artifact.transform.sample.producer.Producer;

            public class App {
                public String getGreeting() {
                    return new Producer().sayHello("Stranger");
                }

                public static void main(String[] args) {
                    System.out.println(new App().getGreeting());
                }
            }
        """

        settingsFile << """
            include 'producer'
        """

        when:
        succeeds(':distZip')

        then:
        with(operations()[0].result.taskPlan) {
            task.taskPath == [":producer:compileJava", ":producer:processResources", ":producer:classes", ":producer:jar", ":compileJava", ":processResources", ":classes", ":jar", ":startScripts", ":distZip"]
            dependencies.taskPath.collect { it.sort() } == [[], [], [":producer:compileJava", ":producer:processResources"], [":producer:classes", ":producer:compileJava"], [":producer:compileJava"], [], [":compileJava", ":processResources"], [":classes", ":compileJava"], [":jar", ":producer:jar"], [":jar", ":producer:jar", ":startScripts"]]
        }
    }

    private List<BuildOperationRecord> operations() {
        def treeOperations = buildOperations.all(CalculateTreeTaskGraphBuildOperationType)
        assert treeOperations.size() == 1

        def buildOperations = buildOperations.all(CalculateTaskGraphBuildOperationType)
        buildOperations.each {
            assert it.parentId == treeOperations.first().id
        }
        return buildOperations
    }

    private BuildOperationRecord operation() {
        def operations = operations()
        assert operations.size() == 1
        return operations[0]
    }
}
