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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.bintray.BintrayApi
import org.gradle.test.fixtures.bintray.BintrayTestServer
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.junit.Rule

class BintrayPluginResolutionIntegTest extends AbstractIntegrationSpec {

    @Rule BintrayTestServer bintray = new BintrayTestServer(executer, file("repo"))
    def pluginBuilder = new PluginBuilder(executer, file("plugin"))

    def setup() {
        bintray.start()
    }

    def "can resolve and use plugin from bintray"() {
        given:
        def group = "org.gradle.test"
        def name = "foo"
        def id = "fake-plugin"
        def version = "2.0"

        bintray.api.expectPackageSearch(id, new BintrayApi.FoundPackage(version, "$group:$name"))

        pluginBuilder.pluginIds[id] = "FakePlugin"

        pluginBuilder.groovy("FakePlugin.groovy") << """
            package $pluginBuilder.packageName

            class FakePlugin implements $Plugin.name<$Project.name> {
                void apply($Project.name project) {
                    project.task("pluginTask") << { println "Output from plugin task" }
                }
            }
        """

        def module = bintray.jcenter.module(group, name, version)
        module.allowAll()
        def artifact = module.artifact([:])
        module.publish()
        pluginBuilder.publishTo(artifact.file)

        buildScript """
          plugins {
            apply plugin: "fake-plugin"
          }
        """

        when:
        succeeds "pluginTask"

        then:
        output.contains "Output from plugin task"
    }

}
