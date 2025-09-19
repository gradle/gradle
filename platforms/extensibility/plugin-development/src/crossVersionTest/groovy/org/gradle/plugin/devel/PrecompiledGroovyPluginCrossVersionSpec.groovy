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
package org.gradle.plugin.devel

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.util.GradleVersion
import org.junit.Assume

class PrecompiledGroovyPluginCrossVersionSpec extends CrossVersionIntegrationSpec {

    private static final String PLUGIN_ID = 'foo.bar.my-plugin'
    private static final String PLUGIN_TASK = 'myTask'

    def setup() {
        settingsFile << """
            pluginManagement {
                repositories {
                    maven {
                        url = "${mavenRepo.uri}"
                    }
                }
            }
        """

        buildFile << """
            plugins {
                id '$PLUGIN_ID' version '1.0'
            }
        """
    }

    def "precompiled Groovy plugin built with current version can be used with Gradle 7.0+"() {
        Assume.assumeTrue(previous.version >= GradleVersion.version('7.3')) // 7.3 is the first to support running on Java 17

        given:
        precompiledGroovyPluginBuiltWith(version(getCurrent()))

        when:
        def result = pluginTaskExecutedWith(version(getPrevious())).run()

        then:
        result.output.contains("$PLUGIN_ID applied")
        result.output.contains("$PLUGIN_TASK executed")
    }

    def "precompiled Groovy plugin built with Gradle 6.4+ can be used with current Gradle version"() {
        Assume.assumeTrue(previous.version >= GradleVersion.version('6.4'))

        given:
        precompiledGroovyPluginBuiltWith(version(getPrevious()))

        when:
        def result = pluginTaskExecutedWith(version(getCurrent())).run()

        then:
        result.output.contains("$PLUGIN_ID applied")
        result.output.contains("$PLUGIN_TASK executed")
    }

    private static GradleExecuter pluginTaskExecutedWith(GradleExecuter executer) {
        return executer.withTasks(PLUGIN_TASK)
    }

    private void precompiledGroovyPluginBuiltWith(GradleExecuter executer) {
        file("plugins/src/main/groovy/${PLUGIN_ID}.gradle") << """
            tasks.register('$PLUGIN_TASK') {
                doLast {
                    println '$PLUGIN_TASK executed'
                }
            }
            println '$PLUGIN_ID applied'
        """
        file("plugins/settings.gradle") << "rootProject.name = 'precompiled-plugin'"
        file("plugins/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
                id 'maven-publish'
            }
            group = 'com.example'
            version = '1.0'
            publishing {
                repositories {
                    maven {
                        url = "${mavenRepo.uri}"
                    }
                }
            }
        """

        executer.inDirectory(file("plugins")).withTasks("publish").run()
        file('plugins').forceDeleteDir()
    }

}
