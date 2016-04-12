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

package org.gradle.plugin.use

import org.gradle.api.internal.file.FileOrUriNotationConverter
import org.gradle.api.internal.file.TestFiles
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder

@LeaksFileHandles
class CustomRepositoryPluginResolverSpec extends AbstractDependencyResolutionTest {

    private publishTestPlugin() {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        // The module which holds the plugin implementation.
        def module = mavenRepo.module("org.example.plugin", "plugin", "1.0")
        def artifactFile = module.artifact([:]).artifactFile
        module.publish()

        // The marker module which depends on the plugin implementation module.
        def marker = mavenRepo.module("org.example.plugin", "org.example.plugin", "1.1")
        marker.dependsOn(module)
        marker.publish()

        def message = "from plugin"
        def taskName = "pluginTask"
        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "org.example.plugin")
        pluginBuilder.publishTo(executer, artifactFile)
    }

    def "can resolve plugin from maven-repo"() {
        given:
        FileOrUriNotationConverter.parser(TestFiles.fileSystem());
        publishTestPlugin()
        buildScript """
          plugins {
              id "org.example.plugin" version "1.1"
          }
        """

        when:
        args("-Dorg.gradle.plugin.repoUrl=${mavenRepo.getRootDir()}")

        then:
        succeeds("pluginTask")
        output.contains("from plugin")
    }

    def "cannot resolve plugin from maven-repo without repoUrl"() {
        given:
        FileOrUriNotationConverter.parser(TestFiles.fileSystem());
        publishTestPlugin()
        buildScript """
          plugins {
              id "org.example.plugin" version "1.1"
          }
        """

        expect:
        fails("pluginTask")
        errorOutput.contains("org.gradle.api.plugins.UnknownPluginException")
    }
}
