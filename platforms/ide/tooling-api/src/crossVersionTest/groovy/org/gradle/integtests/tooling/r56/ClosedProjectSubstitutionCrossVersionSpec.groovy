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

package org.gradle.integtests.tooling.r56

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseWorkspace
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject

import java.util.regex.Pattern

@TargetGradleVersion(">=5.6")
class ClosedProjectSubstitutionCrossVersionSpec extends ToolingApiSpecification {

    def "will substitute and run build dependencies for closed projects on startup"() {
        setup:
        multiProjectBuildInRootFolder("parent", ["child1", "child2"]) {
            buildFile << """
            subprojects {
                apply plugin: 'java-library'
            }
            project(":child1") {
                configurations {
                    testArtifacts
                }
                task testJar(type: Jar) {
                    archiveClassifier = "tests"
                }
                artifacts {
                    testArtifacts testJar
                }
            }
            project(":child2") {
                dependencies {
                    implementation project(":child1");
                    testImplementation project(path: ":child1", configuration: "testArtifacts")
                }
            }
        """
        }

        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseProject>()
        def out = new ByteArrayOutputStream()
        def workspace = eclipseWorkspace([gradleProject("child1", false), gradleProject("child2")])
        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunBuildDependencyTask(workspace), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(workspace), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        def child2 = buildFinishedHandler.result.children.find { it.name == "child2" }
        child2.projectDependencies.isEmpty()
        child2.classpath.collect { it.file.name }.sort() == ['child1-1.0-tests.jar', 'child1-1.0.jar']
        // TODO: verify test-source usage after https://github.com/gradle/gradle/pull/9484 is merged
        //child2.classpath.find { it.file.name == 'child1-1.0.jar' }.classpathAttributes.find { it.name == EclipsePluginConstants.GRADLE_USED_BY_SCOPE_ATTRIBUTE_NAME }.value == "main,test"
        //child2.classpath.find { it.file.name == 'child1-1.0-tests.jar' }.classpathAttributes.find { it.name == EclipsePluginConstants.GRADLE_USED_BY_SCOPE_ATTRIBUTE_NAME }.value == "test"
        taskExecuted(out, ":eclipseClosedDependencies")
        taskExecuted(out, ":child1:testJar")
        taskExecuted(out, ":child1:jar")
    }

    def "build task is not added if no projects are closed"() {
        setup:
        singleProjectBuildInRootFolder("root")
        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseProject>()
        def out = new ByteArrayOutputStream()
        def workspace = eclipseWorkspace([gradleProject("project")])

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunBuildDependencyTask(workspace), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(workspace), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        !taskExecuted(out, ":eclipseClosedDependencies")


    }

    def "task name is deduplicated"() {
        setup:
        multiProjectBuildInRootFolder("root", ["child1", "child2"]) {
            buildFile << """
            subprojects {
                apply plugin: 'java-library'
            }
            task eclipseClosedDependencies (){}
            project(":child2") {
                dependencies {
                    implementation project(":child1");
                }
            }
        """
        }

        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseProject>()
        def out = new ByteArrayOutputStream()
        def workspace = eclipseWorkspace([gradleProject("child1", false), gradleProject("child2")])
        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunBuildDependencyTask(workspace), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(workspace), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        !taskExecuted(out, ":eclipseClosedDependencies")
        taskExecuted(out, ":eclipseClosedDependencies_")

    }

    def "dependencies in minusConfigurations are removed"() {
        setup:
        multiProjectBuildInRootFolder("root", ["child1", "child2"]) {
            buildFile << """
            subprojects {
                apply plugin: 'java-library'
            }
            project(":child2") {
                apply plugin: 'eclipse'
                configurations {
                    toRemove
                }
                eclipse {
                    classpath {
                        minusConfigurations += [ configurations.toRemove ]
                    }
                }
                dependencies {
                    implementation project(":child1");
                    toRemove project(":child1");
                }
            }
        """
        }

        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseProject>()
        def out = new ByteArrayOutputStream()
        def workspace = eclipseWorkspace([gradleProject("child1", false), gradleProject("child2")])
        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunBuildDependencyTask(workspace), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(workspace), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        def child2 = buildFinishedHandler.result.children.find { it.name == "child2" }
        child2.projectDependencies.isEmpty()
        child2.classpath.isEmpty()
        !taskExecuted(out, ":eclipseClosedDependencies")
    }

    @TargetGradleVersion("=5.5")
    def "will not fail against older gradle versions"() {
        setup:
        multiProjectBuildInRootFolder("root", ["child1", "child2"]) {
            buildFile << """
            subprojects {
                apply plugin: 'java-library'
            }
            project(":child2") {
                dependencies {
                    implementation project(":child1");
                }
            }
        """
        }

        def out = new ByteArrayOutputStream()
        def workspace = eclipseWorkspace([gradleProject("child1", false), gradleProject("child2")])
        when:
        withConnection { connection ->
            connection.action(new LoadEclipseModel(workspace))
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        !taskExecuted(out, ":eclipseClosedDependencies")
    }

    def "javadoc and sources can be specified"() {
        setup:
        multiProjectBuildInRootFolder("root", ["child1", "child2"]) {
            buildFile << """
            subprojects {
                apply plugin: 'java-library'
                apply plugin: 'eclipse'
            }
            project(":child1") {
                task sourceJar(type: Jar) {
                    archiveClassifier = "sources"
                    from sourceSets.main.allJava
                }
                task javadocJar(type: Jar) {
                    from tasks.javadoc
                    archiveClassifier = "javadoc"
                }
            }
            project(":child2") {
                dependencies {
                    implementation project(":child1");
                }
                eclipse {
                    classpath.file.whenMerged { cp ->
                        def dep = entries.find { it.path.endsWith('child1') }
                        def sourceJar = this.project(":child1").tasks.getByName("sourceJar")
                        def javadocJar = this.project(":child1").tasks.getByName("javadocJar")
                        dep.buildDependencies(sourceJar, javadocJar)
                        dep.publicationSourcePath = cp.fileReference(sourceJar.archiveFile.get().asFile)
                        dep.publicationJavadocPath = cp.fileReference(javadocJar.archiveFile.get().asFile)
                    }
                }
            }
        """
        }

        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseProject>()
        def out = new ByteArrayOutputStream()
        def workspace = eclipseWorkspace([gradleProject("child1", false), gradleProject("child2")])
        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunBuildDependencyTask(workspace), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(workspace), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        then:
        def child2 = buildFinishedHandler.result.children.find { it.name == "child2" }
        child2.projectDependencies.isEmpty()
        child2.classpath.collect { it.file.name }.sort() == ['child1-1.0.jar']
        child2.classpath[0].source.name == 'child1-1.0-sources.jar'
        child2.classpath[0].javadoc.name == 'child1-1.0-javadoc.jar'
        taskExecuted(out, ":eclipseClosedDependencies")
        taskExecuted(out, ":child1:javadocJar")
        taskExecuted(out, ":child1:sourceJar")

    }


    private static def taskExecuted(ByteArrayOutputStream out, String taskPath) {
        out.toString().find("(?m)> Task ${Pattern.quote(taskPath)}\$") != null
    }

    EclipseWorkspace eclipseWorkspace(List<EclipseWorkspaceProject> projects) {
        new DefaultEclipseWorkspace(temporaryFolder.file("workspace"), projects)
    }

    EclipseWorkspaceProject gradleProject(String name, boolean isOpen = true) {
        project(name, file(name), isOpen)
    }

    EclipseWorkspaceProject project(String name, File location, boolean isOpen = true) {
        new DefaultEclipseWorkspaceProject(name, location, isOpen)
    }

    EclipseWorkspaceProject externalProject(String name) {
        new DefaultEclipseWorkspaceProject(name, temporaryFolder.file("external/$name"))
    }


}
