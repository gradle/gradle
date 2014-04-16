/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.bintray

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.bintray.BintrayApi
import org.gradle.test.fixtures.bintray.BintrayTestServer
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.hamcrest.Matchers
import org.junit.Rule
import spock.lang.Ignore

@Ignore
class BintrayPluginResolutionIntegTest extends AbstractIntegrationSpec {

    @Rule BintrayTestServer bintray = new BintrayTestServer(executer, mavenRepo)
    def pluginBuilder = new PluginBuilder(executer, file("plugin"))

    def setup() {
        executer.requireOwnGradleUserHomeDir() // negate caching behaviour
        bintray.start()
    }

    def "can resolve and use plugin from bintray"() {
        given:
        def group = "org.gradle.test"
        def name = "foo"
        def id = "fake-plugin"
        def version = "2.0"

        bintray.api.expectPackageSearch(id, new BintrayApi.FoundPackage(version, "$group:$name"))

        def module = bintray.jcenter.module(group, name, version)
        module.allowAll()
        def artifact = module.artifact([:])
        module.publish()

        def message = "from plugin"
        def taskName = "pluginTask"
        pluginBuilder.addPluginWithPrintlnTask(taskName, message, id)
        pluginBuilder.publishTo(artifact.file)

        buildScript """
          plugins {
            apply plugin: "$id", version: "2.0"
          }
        """

        when:
        succeeds taskName

        then:
        output.contains message
    }

    def "plugin version number is required"() {
        given:
        def group = "org.gradle.test"
        def name = "foo"
        def id = "fake-plugin"
        def version = "2.0"

        bintray.api.expectPackageSearch(id, new BintrayApi.FoundPackage(version, "$group:$name"))

        def module = bintray.jcenter.module(group, name, version)
        module.allowAll()
        def artifact = module.artifact([:])
        module.publish()

        pluginBuilder.addPlugin("", id)
        pluginBuilder.publishTo(artifact.file)

        buildScript """
          plugins {
            apply plugin: "$id"
          }
        """

        when:
        fails "tasks"

        then:
        failure.assertHasCause("No version number supplied for plugin 'fake-plugin'. A version number must be supplied for plugins resolved from 'https://bintray.com/gradle-plugins-development/gradle-plugins'.")
    }

    def "does not assert has version number if plugin not found"() {
        given:
        def id = "fake-plugin"
        bintray.api.expectPackageSearch(id)
        buildScript """
          plugins {
            apply plugin: "$id"
          }
        """

        when:
        fails "tasks"

        then:
        failure.assertThatCause(Matchers.startsWith("Cannot resolve plugin request"))
    }

}
