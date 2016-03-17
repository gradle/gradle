/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.resolve

import com.google.common.collect.Maps
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule

class GradleApiJarIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    final ConcurrentTestUtil concurrent = new ConcurrentTestUtil(25000)

    def setup() {
        requireGradleHome()
    }

    def "can compile typical Java-based Gradle plugin using Gradle API"() {
        when:
        buildFile << applyJavaPlugin()
        buildFile << fatGradleApiDependency()

        file("src/main/java/MyPlugin.java") << """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    System.out.println("Plugin applied!");
                }
            }
        """

        then:
        succeeds "build"
    }

    def "can compile typical Groovy-based Gradle plugin using Gradle API without having to declare Groovy dependency"() {
        when:
        buildFile << applyGroovyPlugin()
        buildFile << fatGradleApiDependency()

        file("src/main/groovy/MyPlugin.groovy") << customGroovyPlugin()

        then:
        succeeds "build"
    }

    def "can use ProjectBuilder to unit test a plugin"() {
        when:
        buildFile << testableGroovyProject()

        file("src/main/groovy/MyPlugin.groovy") << customGroovyPlugin()

        file("src/test/groovy/MyTest.groovy") << """
            class MyTest extends groovy.util.GroovyTestCase {

                void testCanUseProjectBuilder() {
                    ${ProjectBuilder.name}.builder().build().plugins.apply(MyPlugin)
                }
            }
        """

        then:
        succeeds "build"
    }

    def "cannot compile against classes that are not part of Gradle's public API"() {
        when:
        buildFile << testableGroovyProject()

        file("src/test/groovy/MyTest.groovy") << """
            class MyTest extends groovy.util.GroovyTestCase {

                void testImplIsHidden() {
                    try {
                        getClass().classLoader.loadClass("$Maps.name")
                        assert false : "expected $Maps.name not to be visible"
                    } catch (ClassNotFoundException ignore) {
                        // expected
                    }
                }
            }
        """

        then:
        succeeds "build"
    }

    def "can reliably compile and unit test a plugin that depends on a conflicting version off a non-public Gradle API"() {
        when:
        buildFile << testableGroovyProject()
        buildFile << """
            dependencies {
                compile 'com.google.guava:guava:19.0'
            }
        """

        file("src/main/groovy/MyPlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import com.google.common.collect.Maps

            class MyPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {
                    println Maps.name
                }
            }
        """

        then:
        succeeds "build"
    }

    def "module metadata for a published plugin does not contain reference to Gradle modules"() {
        given:
        buildFile << testableGroovyProject()
        buildFile << """
            apply plugin: 'maven-publish'

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        file("src/main/groovy/MyPlugin.groovy") << customGroovyPlugin()

        when:
        succeeds "build", "generatePomFileForMavenJavaPublication"

        then:
        def xml = new XmlSlurper().parse(file("build/publications/mavenJava/pom-default.xml"))
        xml.dependencies.size() == 1
        def junitDependency = xml.dependencies.children()[0]
        junitDependency.groupId.text() == 'junit'
        junitDependency.artifactId.text() == 'junit'
        junitDependency.version.text() == '4.12'
        junitDependency.scope.text() == 'runtime'
    }

    def "Gradle API and TestKit dependency can be resolved by concurrent Gradle builds"() {
        given:
        def numProjects = 3
        numProjects.times {
            def projectDirName = file("project$it").name
            def projectBuildFile = file("$projectDirName/build.gradle")
            projectBuildFile << testableGroovyProject()

            file("$projectDirName/src/main/groovy/MyPlugin.groovy") << customGroovyPlugin()
            file("$projectDirName/src/test/groovy/MyTest.groovy") << """
                class MyTest extends groovy.util.GroovyTestCase {

                    void testResolveDependencies() { }
                }
            """
        }

        when:
        numProjects.times { count ->
            concurrent.start {
                executer.usingProjectDirectory(file("project$count")).withTasks("build").run()
            }
        }

        then:
        concurrent.finished()
    }

    def "Gradle API and TestKit dependency can be resolved by concurrent tasks within one build"() {
        when:
        def numProjects = 3
        numProjects.times {
            def subProjectDirName = file("sub$it").name
            def subProjectBuildFile = file("$subProjectDirName/build.gradle")
            subProjectBuildFile << testableGroovyProject()
            subProjectBuildFile << """
                task resolveDependencies {
                    doLast {
                        configurations.all*.files()
                    }
                }
            """

            file("$subProjectDirName/src/main/groovy/MyPlugin.groovy") << customGroovyPlugin()
            file("$subProjectDirName/src/test/groovy/MyTest.groovy") << """
                class MyTest extends groovy.util.GroovyTestCase {

                    void testResolveDependencies() { }
                }
            """
        }
        file('settings.gradle') << "include ${(1..numProjects).collect { "'sub$it'" }.join(',')}"

        then:
        args('--parallel')
        succeeds 'resolveDependencies'
    }

    def "Gradle API and TestKit dependencies are not duplicative"() {
        when:
        buildFile << """
            configurations {
                deps
            }

            dependencies {
                deps fatGradleApi(), fatGradleTestKit()
            }

            task resolveDependencyArtifacts {
                doLast {
                    def resolvedArtifacts = configurations.deps.incoming.files.files
                    def uniqueResolvedArtifacts = resolvedArtifacts.unique()
                    assert resolvedArtifacts == uniqueResolvedArtifacts
                }
            }
        """

        then:
        succeeds 'resolveDependencyArtifacts'
    }

    static String applyJavaPlugin() {
        """
            plugins {
                id 'java'
            }
        """
    }

    static String applyGroovyPlugin() {
        """
            plugins {
                id 'groovy'
            }
        """
    }

    static String jcenterRepository() {
        """
            repositories {
                jcenter()
            }
        """
    }

    static String fatGradleApiDependency() {
        """
            dependencies {
                compile fatGradleApi()
            }
        """
    }

    static String testKitDependency() {
        """
            dependencies {
                testCompile fatGradleTestKit()
            }
        """
    }

    static String junitDependency() {
        """
            dependencies {
                compile 'junit:junit:4.12'
            }
        """
    }

    static String customGroovyPlugin() {
        """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {
                    println 'Plugin applied!'
                }
            }
        """
    }

    private String testableGroovyProject() {
        StringBuilder buildFile = new StringBuilder()
        buildFile <<= applyGroovyPlugin()
        buildFile <<= jcenterRepository()
        buildFile <<= fatGradleApiDependency()
        buildFile <<= testKitDependency()
        buildFile <<= junitDependency()
        buildFile.toString()
    }
}