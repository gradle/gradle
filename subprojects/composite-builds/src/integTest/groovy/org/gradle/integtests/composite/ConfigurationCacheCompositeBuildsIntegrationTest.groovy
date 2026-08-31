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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.scan.config.fixtures.ApplyDevelocityPluginFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

@Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "handles CC explicitly")
class ConfigurationCacheCompositeBuildsIntegrationTest extends AbstractIntegrationSpec {

    def configurationCache = new ConfigurationCacheFixture(this)

    @Override
    void setupExecuter(){
        super.setupExecuter()
        executer.withConfigurationCacheEnabled()
    }

    def "can publish Build Scan with composite build"() {
        given:
        withLibBuild()
        withDevelocityPlugin(withAppBuild())

        when:
        inDirectory 'app'
        run 'assemble', '--scan', '-Dscan.dump'

        then:
        postBuildOutputContains 'Build scan written to'
        configurationCache.assertStateStored()

        when:
        inDirectory 'app'
        run 'assemble', '--scan', '-Dscan.dump'

        then:
        postBuildOutputContains 'Build scan written to'
        configurationCache.assertStateLoaded()
    }

    def "can use lib produced by included build"() {
        given:
        withLibBuild()
        withAppBuild()

        when:
        inDirectory 'app'
        run 'run'

        then:
        outputContains 'Before!'
        configurationCache.assertStateStored()

        and: 'included build state is stored in a separate file with the correct permissions'
        def confCacheDir = file("./app/.gradle/configuration-cache")
        confCacheDir.isDirectory()
        def confCacheFiles = confCacheDir.allDescendants().findAll { it != 'configuration-cache.lock' && it != 'gc.properties' }
        confCacheFiles.size() == 14 // header, candidates file, classloader scopes, 2 * fingerprint, build strings file, root build state file, root build shared objects file, included build state file, included build shared objects file, 2 * project state file, 2 * owner-less node state files
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
        run 'run'

        then: 'it should pick up the changes'
        outputContains 'After!'
        configurationCache.assertStateLoaded()
    }

    def "can use lib produced by multi-project included build with custom task"() {
        given:
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
        run 'run'

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
        run 'run'

        then: 'it should pick up the changes'
        outputContains 'custom task...'
        outputContains 'After!'
        configurationCache.assertStateLoaded()
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

        when:
        run 'clean', 'compileJava'

        then:
        configurationCache.assertStateStored()

        when:
        run 'clean', 'compileJava'

        then:
        configurationCache.assertStateLoaded()

        and:
        result.assertTaskOrder(':lib:compileJava', ':util:compileJava', ':compileJava')

        where:
        order << ['lib', 'util'].permutations()
    }

    def "multiple builds can depend on the same task of another included build"() {
        given:
        settingsFile("""
            includeBuild("a")
            includeBuild("b")
            includeBuild("shared")
        """)

        buildFile("""
            plugins {
                id("java-library")
            }
            dependencies {
                implementation("com.example:a:1.0")
                implementation("com.example:b:1.0")
            }
        """)

        file("src/main/java/Root.java") << "public class Root {}"

        ["a", "b"].each { name ->
            createDir(name) {
                file("settings.gradle") << "rootProject.name = '$name'"
                file('build.gradle') << """
                    plugins {
                        id("java-library")
                    }
                    group = "com.example"
                    version = "1.0"
                    dependencies {
                        api("com.example:shared:1.0")
                    }
                """
                file("src/main/java/${name.toUpperCase()}.java") << "public class ${name.toUpperCase()} {}"
            }
        }

        createDir("shared") {
            file("settings.gradle") << "rootProject.name = 'shared'"
            file("build.gradle") << """
                plugins {
                    id("java-library")
                }
                group = "com.example"
                version = "1.0"
            """
            file("src/main/java/Shared.java") << "public class Shared {}"
        }

        when:
        succeeds("compileJava")

        then:
        configurationCache.assertStateStored()

        when:
        file("shared/src/main/java/Shared.java").text = "public class Shared { public static final int CHANGED = 1; }"

        and:
        succeeds("compileJava")

        then:
        configurationCache.assertStateLoaded()

        and:
        result.assertTaskExecuted(":shared:compileJava")
        result.assertTaskOrder(":shared:compileJava", ":a:compileJava", ":compileJava")
        result.assertTaskOrder(":shared:compileJava", ":b:compileJava", ":compileJava")
    }

    def "can depend on task of included plugin build that already ran at configuration time"() {
        given:
        createDir("build-logic") {
            file("settings.gradle") << "rootProject.name = 'build-logic'"
            file("build.gradle") << """
                plugins {
                    id("java-gradle-plugin")
                }
                gradlePlugin {
                    plugins {
                        p {
                            id = "test.plugin"
                            implementationClass = "test.PluginImpl"
                        }
                    }
                }
                tasks.register("notInvolvedInBuildLogic") {
                    doLast {
                        println("notInvolvedInBuildLogic ran")
                    }
                }
            """
            file("src/main/java/test/PluginImpl.java") << """
                package test;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class PluginImpl implements Plugin<Project> {
                    public void apply(Project project) { }
                }
            """
        }

        settingsFile("""
            includeBuild("build-logic")
        """)

        buildFile("""
            plugins {
                id("test.plugin")
            }
            def buildLogic = gradle.includedBuild("build-logic")
            tasks.register("consume") {
                // Ran at configuration time already, in order to build the plugin
                dependsOn(buildLogic.task(":jar"))
                // Not part of the build logic. Only scheduled in the main work graph, so the
                // build-logic build stores a work graph that does not contain the jar task
                dependsOn(buildLogic.task(":notInvolvedInBuildLogic"))
                doLast {
                    println("consume ran")
                }
            }
        """)

        when:
        succeeds("consume")

        then:
        configurationCache.assertStateStored()
        result.assertTaskExecuted(":build-logic:jar")
        result.assertTaskExecuted(":build-logic:notInvolvedInBuildLogic")
        result.assertTaskExecuted(":consume")

        when:
        succeeds("consume")

        then:
        configurationCache.assertStateLoaded()

        and:
        result.assertTasksExecuted(":build-logic:notInvolvedInBuildLogic", ":consume")
    }

    private static withDevelocityPlugin(TestFile settingsDir) {
        ApplyDevelocityPluginFixture.applyDevelocityPlugin(
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
