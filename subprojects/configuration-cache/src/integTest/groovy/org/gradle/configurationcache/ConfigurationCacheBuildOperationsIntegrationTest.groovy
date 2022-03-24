/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.configurationcache.ConfigurationCacheLoadBuildOperationType
import org.gradle.internal.configurationcache.ConfigurationCacheStoreBuildOperationType
import org.gradle.test.fixtures.file.TestFile

class ConfigurationCacheBuildOperationsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits no load/store build operations when configuration cache is not used"() {
        given:
        withLibBuild()
        withAppBuild()

        when:
        inDirectory 'app'
        run 'assemble'

        then:
        operations.all(ConfigurationCacheLoadBuildOperationType).empty
        operations.all(ConfigurationCacheStoreBuildOperationType).empty
    }

    def "emits single load/store build operations of each type per build-tree when configuration cache is used - included build dependency"() {
        given:
        withLibBuild()
        withAppBuild()

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble'

        then:
        operations.all(ConfigurationCacheLoadBuildOperationType).empty
        with(operations.all(ConfigurationCacheStoreBuildOperationType)) {
            size() == 1
            with(get(0)) {
                details == [:]
                it.result == [:]
            }
        }

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble'

        then:
        with(operations.all(ConfigurationCacheLoadBuildOperationType)) {
            size() == 1
            with(get(0)) {
                details == [:]
                it.result == [:]
            }
        }
        operations.all(ConfigurationCacheStoreBuildOperationType).empty
    }

    def "emits single load/store build operations of each type per build-tree when configuration cache is used - included build build logic"() {
        given:
        withLibBuild(true)
        file('settings.gradle') << """
            pluginManagement {
                includeBuild 'lib'
            }
        """
        buildFile << """
            plugins {
                id 'my-plugin'
            }
        """
        when:
        configurationCacheRun 'help'

        then:
        outputContains('In script plugin')
        operations.all(ConfigurationCacheLoadBuildOperationType).empty
        with(operations.all(ConfigurationCacheStoreBuildOperationType)) {
            size() == 1
            with(get(0)) {
                details == [:]
                it.result == [:]
            }
        }

        when:
        configurationCacheRun 'help'

        then:
        with(operations.all(ConfigurationCacheLoadBuildOperationType)) {
            size() == 1
            with(get(0)) {
                details == [:]
                it.result == [:]
            }
        }
        operations.all(ConfigurationCacheStoreBuildOperationType).empty
    }

    private TestFile withLibBuild(boolean withPrecompiledScriptPlugin = false) {
        createDir('lib') {
            file('settings.gradle') << """
                rootProject.name = 'lib'
            """
            if (withPrecompiledScriptPlugin) {
                file('build.gradle') << """
                    plugins { id 'groovy-gradle-plugin' }
                """
                file('src/main/groovy/my-plugin.gradle') << """
                    println 'In script plugin'
                """
            }
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
