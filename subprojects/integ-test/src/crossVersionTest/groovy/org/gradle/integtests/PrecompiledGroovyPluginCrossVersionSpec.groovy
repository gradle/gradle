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
package org.gradle.integtests

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.util.GradleVersion
import org.junit.Assume

class PrecompiledGroovyPluginCrossVersionSpec extends CrossVersionIntegrationSpec {

    private static final String pluginId = 'foo.bar.my-plugin'
    private static final String pluginTask = 'myTask'
    private static final String pluginJarName = 'plugin.jar'

    def setup() {
        settingsFile << """
            buildscript {
                dependencies {
                    classpath(files('$pluginJarName'))
                }
            }
        """

        buildFile << """
            plugins {
                id '$pluginId'
            }
        """
    }

    def cleanup() {
        file(pluginJarName).delete()
    }

    def "precompiled Groovy plugin built with current version can be used with Gradle 6.0+"() {
        Assume.assumeTrue(previous.version >= GradleVersion.version('6.0'))

        given:
        precompiledGroovyPluginBuiltWith(version(getCurrent()))

        when:
        def result = pluginTaskExecutedWith(version(getPrevious())).run()

        then:
        result.output.contains("$pluginId applied")
        result.output.contains("$pluginTask executed")
    }

    def "precompiled Groovy plugin built with Gradle 6.4+ can be used with current Gradle version"() {
        Assume.assumeTrue(previous.version >= GradleVersion.version('6.4'))

        given:
        precompiledGroovyPluginBuiltWith(version(getPrevious()))

        when:
        def result = pluginTaskExecutedWith(version(getCurrent())).run()

        then:
        result.output.contains("$pluginId applied")
        result.output.contains("$pluginTask executed")
    }

    def "can not use a precompiled script plugin with Gradle earlier than 6.0"() {
        Assume.assumeTrue(previous.version < GradleVersion.version('6.0'))

        given:
        precompiledGroovyPluginBuiltWith(version(getCurrent()))

        when:
        def result = pluginTaskExecutedWith(version(getPrevious())).runWithFailure()

        then:
        result.assertHasDescription("Plugin [id: '$pluginId'] was not found in any of the following sources")
        result.assertNotOutput("$pluginId applied")
        result.assertNotOutput("$pluginTask executed")
    }

    private static GradleExecuter pluginTaskExecutedWith(GradleExecuter executer) {
        return executer.withTasks(pluginTask)
    }

    private void precompiledGroovyPluginBuiltWith(GradleExecuter executer) {
        file("plugins/src/main/groovy/${pluginId}.gradle") << """
            tasks.register('$pluginTask') {
                doLast {
                    println '$pluginTask executed'
                }
            }
            println '$pluginId applied'
        """
        file("plugins/settings.gradle") << "rootProject.name = 'precompiled-plugin'"
        file("plugins/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """

        executer.inDirectory(file("plugins")).withTasks("jar").run()
        def pluginJar = file("plugins/build/libs/precompiled-plugin.jar").assertExists()
        def movedJar = file(pluginJarName)
        pluginJar.renameTo(movedJar)
        file('plugins').forceDeleteDir()
    }

}
