/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.plugin.use.resolve.service.PluginResolutionServiceTestServer
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.junit.Rule

class NonDeclarativePluginUseIntegrationSpec extends AbstractIntegrationSpec {

    public static final String PLUGIN_ID = "org.myplugin"
    public static final String VERSION = "1.0"
    public static final String GROUP = "my"
    public static final String ARTIFACT = "plugin"
    public static final String USE = "plugins { id '$PLUGIN_ID' version '$VERSION' }"

    @Rule
    PluginResolutionServiceTestServer service = new PluginResolutionServiceTestServer(executer, mavenRepo)

    def pluginBuilder = new PluginBuilder(file("plugin"))

    def setup() {
        service.start()
    }

    def "non declarative plugin implementation can access core plugins and not core impl"() {
        given:
        publishPlugin """
            project.apply plugin: 'java'

            // Can see plugin classes
            getClass().classLoader.loadClass('org.gradle.api.plugins.JavaPlugin')

            // Can't see core impl classes
            def implClassName = 'com.google.common.collect.Multimap'
            project.getClass().getClassLoader().loadClass(implClassName)

            try {
                getClass().getClassLoader().loadClass(implClassName)
                assert false : "should have failed to load gradle implementation class: \$implClassName"
            } catch (ClassNotFoundException ignore) {

            }

            project.task('pluginTask')
        """

        when:
        buildScript USE

        then:
        succeeds("pluginTask")
    }

    def "plugin implementation and dependencies are visible to plugin and build script"() {
        given:
        def pluginBuilder2 = new PluginBuilder(file("plugin2"))
        pluginBuilder2.addPlugin("project.task('plugin2Task')", "test-plugin-2", "TestPlugin2")

        def module2 = service.m2repo.module(GROUP, ARTIFACT + "2", VERSION)
        pluginBuilder2.publishTo(executer, module2.artifactFile)
        module2.allowAll()

        def module = publishPlugin """
            // can load plugin dependended on
            project.apply plugin: 'test-plugin-2'

            // Can see dependency classes
            getClass().classLoader.loadClass('${pluginBuilder2.packageName}.TestPlugin2')

            project.task('pluginTask')
        """

        module.dependsOn(GROUP, ARTIFACT + "2", VERSION)
        module.publishPom()

        when:
        buildScript """
            $USE

            def ops = []

            plugins.withId('$PLUGIN_ID') {
              ops << "withId 1"
            }

            plugins.withId("test-plugin-2") {
              ops << "withId 2"
            }

            def class1 = ${pluginBuilder.packageName}.TestPlugin
            def class2 = ${pluginBuilder2.packageName}.TestPlugin2

            plugins.withType(class1) {
              ops << "withType 1"
            }

            plugins.withType(class2) {
              ops << "withType 2"
            }

            apply plugin: '$PLUGIN_ID'
            apply plugin: 'test-plugin-2'

            println "ops = \$ops"
        """

        then:
        succeeds("pluginTask", "plugin2Task")

        and:
        output.contains 'ops = [withId 1, withId 2, withType 1, withType 2]'
    }


    MavenHttpModule publishPlugin(String impl) {
        service.expectPluginQuery(PLUGIN_ID, VERSION, GROUP, ARTIFACT, VERSION) {
            legacy = true
        }

        def module = service.m2repo.module(GROUP, ARTIFACT, VERSION)
        module.allowAll()

        pluginBuilder.addPlugin(impl, PLUGIN_ID)
        pluginBuilder.publishTo(executer, module.artifactFile)

        module
    }
}
