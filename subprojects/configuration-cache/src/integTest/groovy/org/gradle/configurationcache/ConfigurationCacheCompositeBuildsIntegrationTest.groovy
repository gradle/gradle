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

import org.gradle.test.fixtures.file.TestFile


class ConfigurationCacheCompositeBuildsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "can use lib produced by included build"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        withAppBuild()
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

        when:
        inDirectory 'app'
        configurationCacheRunLenient 'run'

        then:
        outputContains 'Before!'
        configurationCache.assertStateStored()

        when: 'changing source file from included build'
        file('lib/src/main/java/Lib.java').text = """
            public class Lib { public static void main() {
                System.out.println("After!");
            } }
        """

        and: 'rerunning the build'
        inDirectory 'app'
        configurationCacheRunLenient 'run'

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
        configurationCacheRunLenient 'run'

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
        configurationCacheRunLenient 'run'

        then: 'it should pick up the changes'
        outputContains 'custom task...'
        outputContains 'After!'
        configurationCache.assertStateLoaded()
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
        problems.assertFailureHasProblems(failure) {
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
}
