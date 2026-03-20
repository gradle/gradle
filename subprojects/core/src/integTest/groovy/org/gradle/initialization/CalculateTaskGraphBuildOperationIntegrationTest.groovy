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

import org.gradle.initialization.buildsrc.BuildBuildSrcBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationNotificationsFixture
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.launcher.exec.RunBuildBuildOperationType

import static org.gradle.integtests.fixtures.TestableBuildOperationRecord.buildOp

class CalculateTaskGraphBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    final buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @SuppressWarnings("GroovyUnusedDeclaration")
    final operationNotificationsFixture = new BuildOperationNotificationsFixture(executer, temporaryFolder)

    def "requested and filtered tasks are exposed"() {
        def subprojects = ["a", "b", "a/c"]
        createDirs(*subprojects)
        settingsFile """
            include "a"
            include "b"
            include "a:c"
        """

        (subprojects + ".").each { dir ->
            buildFile "$dir/build.gradle", """
                task otherTask
                task someTask
                someTask.dependsOn otherTask
            """
        }

        when:
        succeeds('help')

        then:
        calculateTaskGraphResult().requestedTaskPaths == [":help"]
        calculateTaskGraphResult().excludedTaskPaths == []

        when:
        succeeds('someTask')

        then:
        calculateTaskGraphResult().requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        calculateTaskGraphResult().excludedTaskPaths == []

        when:
        succeeds('someTask', '-x', ':b:someTask')

        then:
        calculateTaskGraphResult().requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        calculateTaskGraphResult().excludedTaskPaths == [":b:someTask"]

        when:
        succeeds('someTask', '-x', 'otherTask')

        then:
        calculateTaskGraphResult().requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        calculateTaskGraphResult().excludedTaskPaths == [":a:c:otherTask", ":a:otherTask", ":b:otherTask", ":otherTask"]

        when:
        succeeds(':a:someTask')

        then:
        calculateTaskGraphResult().requestedTaskPaths == [":a:someTask"]
        calculateTaskGraphResult().excludedTaskPaths == []
    }

    def "task plan is exposed"() {
        def subprojects = ["a", "b", "a/c"]
        createDirs(*subprojects)
        settingsFile """
            include "a"
            include "b"
            include "a:c"
        """

        (subprojects + ".").each { dir ->
            buildFile "$dir/build.gradle", """
                task otherTask
                task someTask
                someTask.dependsOn otherTask
            """
        }

        when:
        succeeds('someTask')

        then:
        calculateTaskGraphResult().taskPlan.task.taskPath == [':otherTask', ':someTask', ':a:otherTask', ':a:someTask', ':b:otherTask', ':b:someTask', ':a:c:otherTask', ':a:c:someTask']
    }

    def "errors in calculating task graph are exposed"() {
        when:
        fails('someNonexistent')

        then:
        buildOperations.only(CalculateTaskGraphBuildOperationType).failure.contains("Task 'someNonexistent' not found in root project")
    }

    def "build path for calculated task graph is exposed"() {
        createDirs("b")
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

        then:
        def root = buildOperations.root(RunBuildBuildOperationType)

        def treeOperations = buildOperations.all(CalculateTreeTaskGraphBuildOperationType)
        def expectedTreeOperations = [
            buildOp(displayName: "Calculate build tree task graph", parent: buildOperations.first(BuildBuildSrcBuildOperationType)),
            buildOp(displayName: "Calculate build tree task graph", parent: root)
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTreeOperations << buildOp(displayName: "Calculate build tree task graph", parent: buildOperations.only("Load configuration cache state"))
        }
        treeOperations == expectedTreeOperations

        def taskGraphCalculations = buildOperations.all(CalculateTaskGraphBuildOperationType)
        def expectedTaskGraphCalculations = [
            buildOp(displayName: "Calculate task graph (:buildSrc)", parent: treeOperations[0], details: [buildPath: ":buildSrc"], result: [requestedTaskPaths: [":jar"]]),
            buildOp(displayName: "Calculate task graph", parent: treeOperations[1], details: [buildPath: ":"], result: [requestedTaskPaths: [":build"]]),
            buildOp(displayName: "Calculate task graph (:b)", parent: treeOperations[1], details: [buildPath: ":b"], result: [requestedTaskPaths: [":compileJava", ":jar"]])
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTaskGraphCalculations += [
                buildOp(displayName: "Calculate task graph", parent: treeOperations[2], details: [buildPath: ":"], result: [requestedTaskPaths: [":build"]]),
                buildOp(displayName: "Calculate task graph (:b)", parent: treeOperations[2], details: [buildPath: ":b"], result: [requestedTaskPaths: [":compileJava", ":jar"]]),
            ]
        }
        taskGraphCalculations == expectedTaskGraphCalculations
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
        createDirs("included-build")
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
        def results = calculateTaskGraphResults()
        results.size() == 2
        with(results[0].taskPlan) {
            task.taskPath == [":compileJava", ":processResources", ":classes", ":independentTask", ":anotherTask", ":otherTask", ":someTask", ":lastTask"]
            task.buildPath == [":", ":", ":", ":", ":", ":", ":", ":"]
            nodeDependencies.taskPath.collect { it.sort() } == [[":compileJava"], [], [":compileJava", ":processResources"], [], [], [], [":anotherTask", ":otherTask"], []]
            nodeDependencies.buildPath == [[":included-build"], [], [":", ":"], [], [], [], [":", ":"], []]
            finalizedBy.taskPath == [[], [], [], [], [], [], [":lastTask"], []]
            mustRunAfter.taskPath == [[], [], [], [], [], [], [":firstTask"], []]
            shouldRunAfter.taskPath == [[], [], [], [], [], [], [":secondTask"], []]
        }
        with(results[1].taskPlan) {
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

        createDirs("producer")
        settingsFile << """
            include 'producer'
        """

        when:
        succeeds(':distZip')

        then:
        with(calculateTaskGraphResult().taskPlan) {
            task.taskPath == [":producer:compileJava", ":producer:processResources", ":producer:classes", ":producer:jar", ":compileJava", ":processResources", ":classes", ":jar", ":startScripts", ":distZip"]
            nodeDependencies.taskPath.collect { it.sort() } == [[], [], [":producer:compileJava", ":producer:processResources"], [":producer:classes", ":producer:compileJava"], [":producer:compileJava"], [], [":compileJava", ":processResources"], [":classes", ":compileJava"], [":jar", ":producer:jar"], [":jar", ":producer:jar", ":startScripts"]]
        }
    }

    private List<Map<String, ?>> calculateTaskGraphResults() {
        def treeOperations = buildOperations.all(CalculateTreeTaskGraphBuildOperationType)
        assert treeOperations.size() == (GradleContextualExecuter.configCache ? 2 : 1)

        def buildOperations = buildOperations.all(CalculateTaskGraphBuildOperationType) {
            GradleContextualExecuter.notConfigCache || it.parentId != treeOperations.last().id
        }
        buildOperations.each {
            assert it.parentId == treeOperations.first().id
        }
        return buildOperations*.result
    }

    private Map<String, ?> calculateTaskGraphResult() {
        def results = calculateTaskGraphResults()
        assert results.size() == 1
        return results[0]
    }
}
