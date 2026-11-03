/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r910

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r56.DefaultEclipseWorkspace
import org.gradle.integtests.tooling.r56.DefaultEclipseWorkspaceProject
import org.gradle.integtests.tooling.r56.IntermediateResultHandlerCollector
import org.gradle.integtests.tooling.r56.LoadEclipseModel
import org.gradle.integtests.tooling.r56.TellGradleToRunBuildDependencyTask
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Issue

@TargetGradleVersion(">=9.10")
@Issue("https://github.com/gradle/gradle/issues/32284")
class ToolingApiEclipseModelProjectDependenciesCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        includeProjects("a", "b")
        file("a/build.gradle") << """
            plugins {
                id 'java-library'
                id 'eclipse'
            }
        """
        file("b/build.gradle") << """
            plugins {
                id 'java-library'
            }
        """
    }

    def "project dependency declared on #declaredOn has test attribute #hasTestAttribute"() {
        given:
        file("a/build.gradle") << """
            dependencies {
                ${declaredOn.collect { "$it project(':b')" }.join("\n")}
            }
        """

        when:
        def a = projectA()

        then:
        a.projectDependencies*.path == ["b"]
        a.projectDependencies[0].classpathAttributes.any { it.name == "test" && it.value == "true" } == hasTestAttribute

        where:
        declaredOn                            | hasTestAttribute
        ["compileOnly"]                       | false
        ["testCompileOnly"]                   | true
        ["compileOnly", "testImplementation"] | false
    }

    def "minus configuration removes project dependency declared on #configuration"() {
        given:
        file("a/build.gradle") << """
            configurations {
                toRemove
            }
            dependencies {
                ${configuration} project(':b')
                toRemove project(':b')
            }
            eclipse.classpath.minusConfigurations += [configurations.toRemove]
        """

        when:
        def a = projectA()

        then:
        a.projectDependencies.isEmpty()
        a.classpath.isEmpty()

        where:
        configuration << ["implementation", "compileOnly"]
    }

    def "subtracting one artifact of a project keeps the other artifacts of that project"() {
        given:
        file("b/build.gradle") << """
            configurations {
                testArtifacts
            }
            def testJar = tasks.register('testJar', Jar) {
                archiveClassifier = 'tests'
            }
            artifacts {
                testArtifacts testJar
            }
        """
        file("a/build.gradle") << """
            configurations {
                toRemove
            }
            dependencies {
                implementation project(':b')
                testImplementation project(path: ':b', configuration: 'testArtifacts')
                toRemove project(path: ':b', configuration: 'testArtifacts')
            }
            eclipse.classpath.minusConfigurations += [configurations.toRemove]
        """

        when:
        def a = projectA()

        then:
        a.projectDependencies*.path == ["b"]
    }

    def "project artifacts that are not class directories or jars stay off the classpath"() {
        given:
        includeProjects("dist")
        file("dist/build.gradle") << """
            plugins {
                id 'base'
            }
            configurations {
                zipDist
            }
            def distZip = tasks.register('distZip', Zip) {
                archiveBaseName = 'dist'
                destinationDirectory = layout.buildDirectory.dir('distributions')
            }
            artifacts {
                zipDist distZip
            }
        """
        file("a/build.gradle") << """
            dependencies {
                implementation project(path: ':dist', configuration: 'zipDist')
            }
        """

        when:
        def a = projectA()

        then:
        a.projectDependencies.isEmpty()
        a.classpath.isEmpty()
    }

    def "closed compile-only project contributes all of its class directories"() {
        given:
        //Project b
        file("b/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                implementation localGroovy()
            }
        """
        file("b/src/main/java/JavaClass.java") << "public class JavaClass {}"
        file("b/src/main/groovy/GroovyClass.groovy") << "class GroovyClass {}"

        //Project a
        file("a/build.gradle") << """
            dependencies {
                compileOnly project(':b')
            }
        """

        when:
        def a = projectAWithClosedB()

        then:
        a.projectDependencies.isEmpty()
        a.classpath.collect { pathInB(it.file) } as Set == ["build/classes/java/main", "build/classes/groovy/main"] as Set
    }

    def "closed project on compile-only and test configurations appears once and is not a test dependency"() {
        given:
        file("a/build.gradle") << """
            dependencies {
                compileOnly project(':b')
                testImplementation project(':b')
            }
        """

        when:
        def a = projectAWithClosedB()

        then:
        a.projectDependencies.isEmpty()
        a.classpath.collect { pathInB(it.file) }.size() == 1
        !a.classpath[0].classpathAttributes.any { it.name == "test" && it.value == "true" }
    }

    private EclipseProject projectA() {
        loadToolingModel(EclipseProject).children.find { it.name == "a" }
    }

    private EclipseProject projectAWithClosedB() {
        def workspace = new DefaultEclipseWorkspace(temporaryFolder.file("workspace"), [
            new DefaultEclipseWorkspaceProject("a", file("a"), true),
            new DefaultEclipseWorkspaceProject("b", file("b"), false)
        ])
        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<EclipseProject>()
        withConnection { connection ->
            connection.action().projectsLoaded(new TellGradleToRunBuildDependencyTask(workspace), projectsLoadedHandler)
                .buildFinished(new LoadEclipseModel(workspace), buildFinishedHandler)
                .build()
                .forTasks()
                .run()
        }
        buildFinishedHandler.result.children.find { it.name == "a" }
    }

    private String pathInB(File classpathFile) {
        file("b").toPath().relativize(classpathFile.toPath()).toString().replace(File.separatorChar, (char) '/')
    }
}
