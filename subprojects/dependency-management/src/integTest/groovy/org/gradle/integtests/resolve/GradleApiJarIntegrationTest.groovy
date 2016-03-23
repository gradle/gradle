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
import groovy.transform.TupleConstructor
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Unroll

class GradleApiJarIntegrationTest extends AbstractIntegrationSpec {

    private static final String API_JAR_GENERATION_OUTPUT_REGEX = "Generating JAR file 'gradle-api-(.*)\\.jar"
    private static final String TESTKIT_GENERATION_OUTPUT_REGEX = "Generating JAR file 'gradle-test-kit-(.*)\\.jar"

    @Rule
    final ConcurrentTestUtil concurrent = new ConcurrentTestUtil(35000)

    def setup() {
        executer.requireGradleHome().withStackTraceChecksDisabled()
        executer.withEnvironmentVars(GRADLE_USER_HOME: executer.gradleUserHomeDir.absolutePath)
    }

    def "Gradle API dependency resolves the expected JAR files"() {
        expect:
        buildFile << """
            configurations {
                deps
            }

            dependencies {
                deps fatGradleApi()
            }

            task resolveDependencyArtifacts {
                doLast {
                    def resolvedArtifacts = configurations.deps.incoming.files.files
                    assert resolvedArtifacts.size() == 3
                    assert resolvedArtifacts.find { (it.name =~ 'gradle-api-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.find { (it.name =~ 'gradle-installation-beacon-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.find { (it.name =~ 'groovy-all-(.*)\\\\.jar').matches() }
                }
            }
        """

        succeeds 'resolveDependencyArtifacts'
    }

    def "can compile typical Java-based Gradle plugin using Gradle API"() {
        when:
        buildFile << applyJavaPlugin()
        buildFile << fatGradleApiDependency()

        file('src/main/java/MyPlugin.java') << """
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
        succeeds 'build'
    }

    def "can compile typical Groovy-based Gradle plugin using Gradle API without having to declare Groovy dependency"() {
        when:
        buildFile << applyGroovyPlugin()
        buildFile << fatGradleApiDependency()

        file('src/main/groovy/MyPlugin.groovy') << customGroovyPlugin()

        then:
        succeeds 'build'
    }

    def "can use ProjectBuilder to unit test a plugin"() {
        when:
        buildFile << testableGroovyProject()

        file('src/main/groovy/MyPlugin.groovy') << customGroovyPlugin()

        file('src/test/groovy/MyTest.groovy') << """
            class MyTest extends groovy.util.GroovyTestCase {

                void testCanUseProjectBuilder() {
                    ${ProjectBuilder.name}.builder().build().plugins.apply(MyPlugin)
                }
            }
        """

        then:
        succeeds 'build'
    }

    def "cannot compile against classes that are not part of Gradle's public API"() {
        when:
        buildFile << testableGroovyProject()

        file('src/test/groovy/MyTest.groovy') << """
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
        succeeds 'build'
    }

    def "can reliably compile and unit test a plugin that depends on a conflicting version off a non-public Gradle API"() {
        when:
        buildFile << testableGroovyProject()
        buildFile << """
            dependencies {
                compile 'com.google.guava:guava:19.0'
            }
        """

        file('src/main/groovy/MyPlugin.groovy') << """
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
        succeeds 'build'
    }

    def "module metadata generated by Maven plugin does not contain reference to Gradle modules for a published plugin"() {
        given:
        using m2
        buildFile << testableGroovyProject()
        buildFile << """
            group = 'org.gradle.test'
            version = '1.0'
            apply plugin: 'maven'

            uploadArchives {
                repositories {
                    mavenDeployer {
                        repository(url: '$mavenRepo.uri')
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'sample'"

        file('src/main/groovy/MyPlugin.groovy') << customGroovyPlugin()

        when:
        succeeds 'uploadArchives'

        then:
        def module = mavenRepo.module('org.gradle.test', 'sample', '1.0')
        def pom = module.parsedPom
        def compileScope = pom.scopes.test
        compileScope.dependencies.size() == 1
        compileScope.expectDependency('junit:junit:4.12')
    }

    def "module metadata generated by Ivy plugin does not contain reference to Gradle modules for a published plugin"() {
        given:
        buildFile << testableGroovyProject()
        buildFile << """
            group = 'org.gradle.test'
            version = '1.0'

            uploadArchives {
                repositories {
                    ivy {
                        url '${ivyRepo.uri}'
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'sample'"

        file('src/main/groovy/MyPlugin.groovy') << customGroovyPlugin()

        when:
        succeeds 'uploadArchives'

        then:
        def module = ivyRepo.module('org.gradle.test', 'sample', '1.0')
        def ivy = module.parsedIvy
        ivy.dependencies.size() == 1
        ivy.dependencies['junit:junit:4.12'].hasConf('testCompile->default')
    }

    def "module metadata generated by Maven publish plugin does not contain reference to Gradle modules for a published plugin"() {
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

        file('src/main/groovy/MyPlugin.groovy') << customGroovyPlugin()

        when:
        succeeds 'generatePomFileForMavenJavaPublication'

        then:
        def xml = new XmlSlurper().parse(file('build/publications/mavenJava/pom-default.xml'))
        xml.dependencies.size() == 0
    }

    def "module metadata generated by Ivy publish plugin does not contain reference to Gradle modules for a published plugin"() {
        given:
        buildFile << testableGroovyProject()
        buildFile << """
            apply plugin: 'ivy-publish'

            publishing {
                publications {
                    ivyJava(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        file('src/main/groovy/MyPlugin.groovy') << customGroovyPlugin()

        when:
        succeeds 'generateDescriptorFileForIvyJavaPublication'

        then:
        def xml = new XmlSlurper().parse(file('build/publications/ivyJava/ivy.xml'))
        xml.dependencies.children().size() == 0
    }

    def "Gradle API and TestKit dependency can be resolved by concurrent Gradle builds"() {
        given:
        requireOwnGradleUserHomeDir()

        when:
        def outputs = []

        5.times { count ->
            concurrent.start {
                def projectDirName = file("project$count").name
                def projectBuildFile = file("$projectDirName/build.gradle")
                projectBuildFile << resolveGradleApiAndTestKitDependencies()

                def executionResult = executer.inDirectory(file("project$count")).withTasks('resolveDependencies').run()
                outputs << executionResult.output
            }
        }

        concurrent.finished()

        then:
        def apiGenerationOutputs = outputs.findAll { it =~ /$API_JAR_GENERATION_OUTPUT_REGEX/ }
        def testKitGenerationOutputs = outputs.findAll { it =~ /$TESTKIT_GENERATION_OUTPUT_REGEX/ }
        apiGenerationOutputs.size() == 1
        testKitGenerationOutputs.size() == 1
        assertApiGenerationOutput(apiGenerationOutputs[0])
        assertTestKitGenerationOutput(testKitGenerationOutputs[0])
    }

    def "Gradle API and TestKit dependency can be resolved by concurrent tasks within one build"() {
        given:
        requireOwnGradleUserHomeDir()

        buildFile << """
            subprojects {
                ${resolveGradleApiAndTestKitDependencies()}
            }
        """

        file('settings.gradle') << "include ${(1..10).collect { "'sub$it'" }.join(',')}"

        when:
        args('--parallel')
        def result = succeeds 'resolveDependencies'

        then:
        assertApiGenerationOutput(result.output)
        assertTestKitGenerationOutput(result.output)
    }

    def "Gradle API and TestKit dependencies are not duplicative"() {
        when:
        buildFile << """
            configurations {
                gradleImplDeps
            }

            dependencies {
                gradleImplDeps fatGradleApi(), fatGradleTestKit()
            }

            task resolveDependencyArtifacts {
                doLast {
                    def resolvedArtifacts = configurations.gradleImplDeps.incoming.files.files
                    def uniqueResolvedArtifacts = resolvedArtifacts.unique()
                    assert resolvedArtifacts == uniqueResolvedArtifacts
                }
            }
        """

        then:
        succeeds 'resolveDependencyArtifacts'
    }

    @Unroll
    def "Gradle API and TestKit are compatible regardless of order [#description]"() {
        when:
        buildFile << applyGroovyPlugin()
        buildFile << jcenterRepository()
        buildFile << spockDependency()

        dependencyTuples.each {
            buildFile << """
                dependencies.add('$it.configurationName', dependencies.$it.dependencyNotation)
            """
        }

        file('src/test/groovy/BuildLogicFunctionalTest.groovy') << """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import org.junit.Rule
            import org.junit.rules.TemporaryFolder
            import spock.lang.Specification

            class BuildLogicFunctionalTest extends Specification {
                @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
                File buildFile

                def setup() {
                    buildFile = testProjectDir.newFile('build.gradle')
                }

                def "hello world task prints hello world"() {
                    given:
                    buildFile << '''
                        task helloWorld {
                            doLast {
                                println 'Hello world!'
                            }
                        }
                    '''

                    when:
                    def result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments('helloWorld')
                        .build()

                    then:
                    result.output.contains('Hello world!')
                    result.task(':helloWorld').outcome == SUCCESS
                }
            }
        """

        then:
        succeeds 'build'

        where:
        description                          | dependencyTuples
        'fatGradleApi(), fatGradleTestKit()' | [new GradleDependency('compile', 'fatGradleApi()'), new GradleDependency('testCompile', 'fatGradleTestKit()')]
        'fatGradleTestKit(), fatGradleApi()' | [new GradleDependency('testCompile', 'fatGradleTestKit()'), new GradleDependency('compile', 'fatGradleApi()')]
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

    static String fatTestKitDependency() {
        """
            dependencies {
                testCompile fatGradleTestKit()
            }
        """
    }

    static String junitDependency() {
        """
            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """
    }

    static String spockDependency() {
        """
            dependencies {
                testCompile('org.spockframework:spock-core:1.0-groovy-2.4') {
                    exclude module: 'groovy-all'
                }
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

    static String resolveGradleApiAndTestKitDependencies() {
        """
            configurations {
                gradleImplDeps
            }

            dependencies {
                gradleImplDeps fatGradleApi(), fatGradleTestKit()
            }

            task resolveDependencies {
                doLast {
                    configurations.gradleImplDeps.resolve()
                }
            }
        """
    }

    private String testableGroovyProject() {
        StringBuilder buildFile = new StringBuilder()
        buildFile <<= applyGroovyPlugin()
        buildFile <<= jcenterRepository()
        buildFile <<= fatGradleApiDependency()
        buildFile <<= fatTestKitDependency()
        buildFile <<= junitDependency()
        buildFile.toString()
    }

    @TupleConstructor
    private static class GradleDependency {
        String configurationName
        String dependencyNotation
    }

    static void assertApiGenerationOutput(String output) {
        assertSingleGenerationOutput(output, API_JAR_GENERATION_OUTPUT_REGEX)
    }

    static void assertTestKitGenerationOutput(String output) {
        assertSingleGenerationOutput(output, TESTKIT_GENERATION_OUTPUT_REGEX)
    }

    static void assertSingleGenerationOutput(String output, String regex) {
        def pattern = /\b${regex}\b/
        def matcher = output =~ pattern
        assert matcher.count == 1
    }
}