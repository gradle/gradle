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
import org.gradle.api.Action
import org.gradle.internal.ErroringAction
import org.gradle.internal.IoActions
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@Requires(IntegTestPreconditions.NotEmbeddedExecutor) // Gradle API and TestKit JARs are not generated when running embedded
class GradleImplDepsCompatibilityIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    def "TestKit dependency artifacts contain Gradle API artifact"() {
        given:
        buildFile << """
            configurations {
                gradleApi
                testKit
            }

            dependencies {
                gradleApi gradleApi()
                testKit gradleTestKit()
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

    def "Gradle API dependency does not contain any of TestKit dependency classes and vice versa"() {
        given:
        def outputDirPath = 'build/output'
        buildFile << """
            configurations {
                gradleApi
                testKit
            }

            dependencies {
                gradleApi gradleApi()
                testKit gradleTestKit()
            }

            task resolveDependencyArtifacts {
                doLast {
                    copy {
                        into '$outputDirPath'
                        from configurations.gradleApi.resolve().find { it.name.startsWith('gradle-api-') }
                        from configurations.testKit.resolve().find { it.name.startsWith('gradle-test-kit-') }
                    }
                }
            }
        """

        when:
        succeeds 'resolveDependencyArtifacts'

        then:
        def outputDir = temporaryFolder.testDirectory.file(outputDirPath)
        def jarFiles = outputDir.listFiles()
        jarFiles.size() == 2
        def gradleApiJar = jarFiles.find { it.name.startsWith('gradle-api-') }
        def testKitJar = jarFiles.find { it.name.startsWith('gradle-test-kit-') }
        File gradleApiClassNamesTextFile = temporaryFolder.testDirectory.file("$outputDirPath/gradle-api-classes.txt")
        File testKitClassNamesTextFile = temporaryFolder.testDirectory.file("$outputDirPath/testkit-classes.txt")
        writeClassesInZipFileToTextFile(gradleApiJar, gradleApiClassNamesTextFile)
        writeClassesInZipFileToTextFile(testKitJar, testKitClassNamesTextFile)
        assert gradleApiClassNamesTextFile.size() > 0
        assert testKitClassNamesTextFile.size() > 0
        verifyNoDuplicateEntries(gradleApiClassNamesTextFile, testKitClassNamesTextFile)
        verifyNoDuplicateEntries(testKitClassNamesTextFile, gradleApiClassNamesTextFile)
    }

    def "Gradle API and TestKit are compatible regardless of order #dependencyPermutations"() {
        when:
        buildFile << applyGroovyPlugin()
        buildFile << mavenCentralRepository()
        buildFile << """
            testing {
                suites {
                    test {
                        useSpock()
                    }
                }
            }

            repositories {
                maven { url '${buildContext.localRepository.toURI().toURL()}' }
            }
        """

        dependencyPermutations.each {
            buildFile << """
                dependencies {
                    constraints {
                        implementation 'org.gradle:gradle-tooling-api:${distribution.version.baseVersion.version}'
                    }
                }
                dependencies.add('$it.configurationName', $it.dependencyNotation)
            """
        }

        file('src/test/groovy/BuildLogicFunctionalTest.groovy') << """
            import org.gradle.testkit.runner.GradleRunner
            import static org.gradle.testkit.runner.TaskOutcome.*
            import spock.lang.Specification
            import spock.lang.TempDir

            class BuildLogicFunctionalTest extends Specification {
                @TempDir File testProjectDir
                File buildFile

                def setup() {
                    buildFile = new File(testProjectDir, 'build.gradle')
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
                        .withProjectDir(testProjectDir)
                        .withArguments('helloWorld')
                        .withTestKitDir(new java.io.File("${TextUtil.normaliseFileSeparators(executer.gradleUserHomeDir.absolutePath)}"))
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
        dependencyPermutations << [new GradleDependency('Gradle API', 'implementation', 'dependencies.gradleApi()'),
                                   new GradleDependency('TestKit', 'testImplementation', 'dependencies.gradleTestKit()'),
                                   new GradleDependency('Tooling API', 'implementation', "'org.gradle:gradle-tooling-api'")].permutations()
    }

    static void writeClassesInZipFileToTextFile(File zipFile, File txtFile) {
        IoActions.withResource(new ZipInputStream(new FileInputStream(zipFile)), new ErroringAction<ZipInputStream>() {
            protected void doExecute(ZipInputStream inputStream) throws Exception {
                ZipEntry zipEntry = inputStream.getNextEntry()
                while (zipEntry != null) {
                    String name = zipEntry.name

                    // only check Gradle core, non-relocated classes
                    if (name.endsWith('.class') && name.startsWith('org/gradle/') && !name.startsWith('org/gradle/impldep/')) {
                        txtFile << "$name\n"
                    }

                    zipEntry = inputStream.getNextEntry()
                }
            }
        })
    }

    static void verifyNoDuplicateEntries(File textFileA, File textFileB) {
        IoActions.withResource(new BufferedReader(new FileReader(textFileA)), new Action<BufferedReader>() {
            void execute(BufferedReader bufferedReaderA) {
                String lineA
                while ((lineA = bufferedReaderA.readLine()) != null) {
                    IoActions.withResource(new BufferedReader(new FileReader(textFileB)), new Action<BufferedReader>() {
                        void execute(BufferedReader bufferedReaderB) {
                            String lineB
                            while ((lineB = bufferedReaderB.readLine()) != null) {
                                assert !(lineA == lineB)
                            }
                        }
                    })
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
