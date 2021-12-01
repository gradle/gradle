/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.integtests.fixtures.BuildOperationTreeQueries
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.scan.config.fixtures.ApplyGradleEnterprisePluginFixture
import org.gradle.test.fixtures.file.TestFile

import java.util.regex.Pattern

class ConfigurationCacheCompositeBuildsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "can publish build scan with composite build"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        withLibBuild()
        withEnterprisePlugin(withAppBuild())

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble', '--scan', '-Dscan.dump'

        then:
        postBuildOutputContains 'Build scan written to'
        configurationCache.assertStateStored()

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble', '--scan', '-Dscan.dump'

        then:
        postBuildOutputContains 'Build scan written to'
        configurationCache.assertStateLoaded()
    }

    def "hierarchy of build scan relevant build operations is preserved"() {
        given:
        def expectedOperations = [
            "Run build / Load build",
            "Run build / Load build / Evaluate settings",
            "Run build / Load build / Load build (:lib)",
            "Run build / Load build / Load build (:lib) / Evaluate settings (:lib)",
            "Run build / Configure build / Load projects",
            "Run build / Configure build / Configure build (:lib) / Load projects",
            "Run build / Configure build / Configure build (:lib) / Configure project :lib",
            "Run build / Configure build / Configure project :",
            "Run build / Calculate build tree task graph",
            "Run build / Calculate build tree task graph / Calculate task graph",
            "Run build / Calculate build tree task graph / Calculate task graph (:lib)",
            "Run build / Calculate build tree task graph / Notify task graph whenReady listeners (:lib)",
            "Run build / Calculate build tree task graph / Notify task graph whenReady listeners"
        ]
        def configurationCache = newConfigurationCacheFixture()
        withLibBuild()
        withAppBuild()

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble'

        then:
        configurationCache.assertStateStored()
        def buildScanOperationsOnStore = buildScanOperationsOf(configurationCache.operations)
        buildScanOperationsOnStore == expectedOperations

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble'

        then:
        configurationCache.assertStateLoaded()
        def buildScanOperationsOnLoad = buildScanOperationsOf(configurationCache.operations)
        buildScanOperationsOnLoad == buildScanOperationsOnStore
    }

    private static List<?> buildScanOperationsOf(BuildOperationTreeQueries operations) {
        scanRelevantOperationsIn(operations).collect {
            (parentsOf(it, operations) + it)
                .collect { it.displayName }
                .join ' / '
        }
    }

    private static List<BuildOperationRecord> parentsOf(BuildOperationRecord buildOperationRecord, BuildOperationTreeQueries operations) {
        operations.parentsOf(buildOperationRecord).findAll {
            // remove intermediate configuration cache state operation from the tree
            it.displayName != 'Load configuration cache state'
        }
    }

    private static List<BuildOperationRecord> scanRelevantOperationsIn(BuildOperationTreeQueries operations) {
        operations.all(
            Pattern.compile(
                /(Load build|Evaluate settings|Load projects|Configure project|Calculate build tree task graph|Calculate task graph|Notify task graph whenReady listeners).*/
            )
        )
    }

    def "can use lib produced by included build"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        withLibBuild()
        withAppBuild()

        when:
        inDirectory 'app'
        configurationCacheRun 'run'

        then:
        outputContains 'Before!'
        configurationCache.assertStateStored()

        and: 'included build state is stored in a separate file with the correct permissions'
        def confCacheDir = file("./app/.gradle/configuration-cache")
        confCacheDir.isDirectory()
        def confCacheFiles = confCacheDir.allDescendants().findAll { it != 'configuration-cache.lock' && it != 'gc.properties' }
        confCacheFiles.size() == 5 // header, 2 * fingerprint, root build state file, included build state file
        if (!OperatingSystem.current().isWindows()) {
            confCacheFiles.forEach {
                assert confCacheDir.file(it).mode == 384
            }
        }

        when: 'changing source file from included build'
        file('lib/src/main/java/Lib.java').text = """
            public class Lib { public static void main() {
                System.out.println("After!");
            } }
        """

        and: 'rerunning the build'
        inDirectory 'app'
        configurationCacheRun 'run'

        then: 'it should pick up the changes'
        outputContains 'After!'
        configurationCache.assertStateLoaded()
    }

    def "can use lib produced by multi-project included build with custom task"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        withAppBuild()
        createDir('lib') {
            file('settings.gradle') << """
                rootProject.name = 'lib-root'
                include 'lib'
            """

            file('lib/build.gradle') << """
                plugins { id 'java' }
                group = 'org.test'
                version = '1.0'

                class CustomTask extends DefaultTask {
                    @TaskAction def act() {
                        println 'custom task...'
                    }
                }

                def customTask = tasks.register('customTask', CustomTask)
                tasks.named('jar') {
                    dependsOn customTask
                }
            """

            file('lib/src/main/java/Lib.java') << """
                public class Lib { public static void main() {
                    System.out.println("Before!");
                } }
            """
        }

        when:
        inDirectory 'app'
        configurationCacheRun 'run'

        then:
        outputContains 'custom task...'
        outputContains 'Before!'
        configurationCache.assertStateStored()

        when: 'changing source file from included build'
        file('lib/lib/src/main/java/Lib.java').text = """
            public class Lib { public static void main() {
                System.out.println("After!");
            } }
        """

        and: 'rerunning the build'
        inDirectory 'app'
        configurationCacheRun 'run'

        then: 'it should pick up the changes'
        outputContains 'custom task...'
        outputContains 'After!'
        configurationCache.assertStateLoaded()
    }

    def "reports a problem when source dependencies are present"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:buildB") {
                        from(GitVersionControlSpec) {
                            url = uri("some-repo")
                        }
                    }
                }
            }
        """

        and:
        def expectedProblem = "Gradle runtime: support for source dependencies is not yet implemented with the configuration cache."

        when:
        configurationCacheFails("help")

        then:
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems(expectedProblem)
            withProblemsWithStackTraceCount(0)
        }

        when:
        configurationCacheRunLenient("help")

        then:
        problems.assertResultHasProblems(result) {
            withUniqueProblems(expectedProblem)
            withProblemsWithStackTraceCount(0)
        }

        when:
        configurationCacheFails("help")

        then:
        configurationCache.assertStateLoaded()
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems(expectedProblem)
            withProblemsWithStackTraceCount(0)
        }

        when:
        configurationCacheRunLenient("help")

        then:
        configurationCache.assertStateLoaded()
        problems.assertResultHasProblems(result) {
            withUniqueProblems(expectedProblem)
            withProblemsWithStackTraceCount(0)
        }
    }

    private static withEnterprisePlugin(TestFile settingsDir) {
        ApplyGradleEnterprisePluginFixture.applyEnterprisePlugin(
            settingsDir.file('settings.gradle')
        )
    }

    private TestFile withLibBuild() {
        createDir('lib') {
            file('settings.gradle') << """
                rootProject.name = 'lib'
            """

            file('build.gradle') << """
                plugins { id 'java' }
                group = 'org.test'
                version = '1.0'
            """

            file('src/main/java/Lib.java') << """
                public class Lib { public static void main() {
                    System.out.println("Before!");
                } }
            """
        }
    }

    private TestFile withAppBuild() {
        createDir('app') {
            file('settings.gradle') << """
                includeBuild '../lib'
            """
            file('build.gradle') << """
                plugins {
                    id 'java'
                    id 'application'
                }
                application {
                   mainClass = 'Main'
                }
                dependencies {
                    implementation 'org.test:lib:1.0'
                }
            """
            file('src/main/java/Main.java') << """
                class Main { public static void main(String[] args) {
                    Lib.main();
                } }
            """
        }
    }
}
