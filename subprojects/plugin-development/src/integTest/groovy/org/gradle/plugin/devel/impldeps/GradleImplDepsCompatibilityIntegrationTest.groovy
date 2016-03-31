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

package org.gradle.plugin.devel.impldeps

import groovy.transform.TupleConstructor
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.ErroringAction
import org.gradle.internal.IoActions
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Unroll

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import static org.gradle.util.TextUtil.normaliseFileSeparators

class GradleImplDepsCompatibilityIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    @Shared
    IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    def "TestKit dependency artifacts contain Gradle API artifact"() {
        given:
        buildFile << """
            configurations {
                gradleApi
                testKit
            }

            dependencies {
                gradleApi fatGradleApi()
                testKit fatGradleTestKit()
            }

            task resolveDependencyArtifacts {
                doLast {
                    def resolvedGradleApiArtifacts = configurations.gradleApi.resolve()
                    def resolvedTestKitArtifacts = configurations.testKit.resolve()
                    def gradleApiJar = resolvedTestKitArtifacts.find { it.name.startsWith('gradle-api-') }
                    assert gradleApiJar != null
                    assert resolvedGradleApiArtifacts.contains(gradleApiJar)
                }
            }
        """

        expect:
        succeeds 'resolveDependencyArtifacts'
    }

    @Ignore("Due to the buggy shading of the TestKit JAR is still happens e.g. org.gradle.api.Incubating")
    def "Gradle API dependency artifact does not contain any of TestKit's classes"() {
        given:
        buildFile << """
            configurations {
                gradleApi
                testKit
            }

            dependencies {
                gradleApi fatGradleApi()
                testKit fatGradleTestKit()
            }

            task resolveDependencyArtifacts {
                doLast {
                    copy {
                        into 'build'
                        from configurations.gradleApi.resolve().find { it.name.startsWith('gradle-api-') }
                        from configurations.testKit.resolve().find { it.name.startsWith('gradle-test-kit-') }
                    }
                }
            }
        """

        when:
        succeeds 'resolveDependencyArtifacts'

        then:
        def outputDir = temporaryFolder.testDirectory.file('build')
        def jarFiles = outputDir.listFiles()
        jarFiles.size() == 2
        def gradleApiJar = jarFiles.find { it.name.startsWith('gradle-api-') }
        def testKitJar = jarFiles.find { it.name.startsWith('gradle-test-kit-') }
        def testKitClassNames = parseClassNamesFromZipFile(testKitJar)
        assertNoDuplicateClassFilenames(gradleApiJar, testKitClassNames)
    }

    @Ignore("Order does matter due to the buggy shading of the TestKit JAR")
    @Unroll
    def "Gradle API and TestKit are compatible regardless of order #dependencyPermutations"() {
        when:
        buildFile << applyGroovyPlugin()
        buildFile << jcenterRepository()
        buildFile << spockDependency()

        dependencyPermutations.each {
            buildFile << """
                dependencies.add('$it.configurationName', $it.dependencyNotation)
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
        dependencyPermutations << [new GradleDependency('Gradle API', 'compile', 'dependencies.fatGradleApi()'),
                                   new GradleDependency('TestKit', 'testCompile', 'dependencies.fatGradleTestKit()'),
                                   new GradleDependency('Tooling API', 'compile', "project.files('${normaliseFileSeparators(buildContext.fatToolingApiJar.absolutePath)}')")].permutations()
    }

    static List<String> parseClassNamesFromZipFile(File zip) {
        def classNames = []

        handleClassFilenameInZip(zip) { classFilename ->
            classNames << classFilename
        }

        classNames
    }

    static void assertNoDuplicateClassFilenames(File zip, List<String> givenClassFilenames) {
        handleClassFilenameInZip(zip) { classFilename ->
            assert !givenClassFilenames.contains(classFilename)
        }
    }

    static void handleClassFilenameInZip(File zip, Closure c) {
        IoActions.withResource(new ZipInputStream(new FileInputStream(zip)), new ErroringAction<ZipInputStream>() {
            protected void doExecute(ZipInputStream inputStream) throws Exception {
                ZipEntry zipEntry = inputStream.getNextEntry()
                while (zipEntry != null) {
                    String name = zipEntry.name

                    if (name.endsWith('.class')) {
                        c(name)
                    }

                    zipEntry = inputStream.getNextEntry()
                }
            }
        })
    }

    @TupleConstructor
    private static class GradleDependency {
        String name
        String configurationName
        String dependencyNotation

        String toString() {
            name
        }
    }
}
