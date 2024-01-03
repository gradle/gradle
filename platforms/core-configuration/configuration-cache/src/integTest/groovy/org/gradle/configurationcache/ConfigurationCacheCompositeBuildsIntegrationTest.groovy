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


import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.scan.config.fixtures.ApplyGradleEnterprisePluginFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

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
            withTotalProblemsCount(2)
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

    @Issue("https://github.com/gradle/gradle/issues/20945")
    def "composite build with dependency substitution can include builds in any order"() {
        given:
        order.each {
            settingsFile "includeBuild '$it'\n"
        }
        buildFile '''
            ['clean', 'compileJava'].each { name ->
                tasks.register(name) {
                    gradle.includedBuilds.each { build ->
                        dependsOn(build.task(':' + name))
                    }
                }
            }
        '''
        createDir('lib') {
            file('settings.gradle') << 'rootProject.name = "lib"'
            file('build.gradle') << '''
                plugins { id 'java-library' }
                group = 'com.example'
                version = '1.0'
            '''
        }
        createDir('util') {
            file('settings.gradle') << 'rootProject.name = "util"'
            file('build.gradle') << '''
                plugins { id 'java-library' }
                dependencies {
                    api 'com.example:lib:1.0'
                }
            '''
        }

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'clean', 'compileJava'

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun 'clean', 'compileJava'

        then:
        configurationCache.assertStateLoaded()

        and:
        result.assertTaskOrder(':lib:compileJava', ':util:compileJava', ':compileJava')

        where:
        order << ['lib', 'util'].permutations()
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
