/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository

class SettingsScriptVisibilityIntegrationTest extends AbstractIntegrationSpec {

    @org.junit.Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def "classes loaded in settings are not visible to buildSrc and are visible to build"() {
        def pluginBuilder = new PluginBuilder(file("plugin"))
        pluginBuilder.addSettingsPlugin("", "test.plugin", "MySettingsPlugin")
        def pluginJar = file("plugin.jar")
        pluginBuilder.publishTo(executer, pluginJar)
        def pluginClassName = "${pluginBuilder.packageName}.MySettingsPlugin"

        settingsFile << "buildscript { dependencies { classpath files(\"plugin.jar\") } }"
        file("buildSrc/build.gradle") << """
            try {
               getClass().classLoader.loadClass('${pluginClassName}')
            } catch (ClassNotFoundException e) {
                println "buildSrc: not visible"    
            }
        """
        file("build.gradle") << """
            getClass().classLoader.loadClass('${pluginClassName}')
            println "build: visible"
        """

        when:
        succeeds("tasks")

        then:
        outputContains("buildSrc: not visible")
        outputContains("build: visible")
    }

}
