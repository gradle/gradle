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

import com.google.common.base.Splitter
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Shared

@LeaksFileHandles
class ResolvingFromMultipleCustomPluginRepositorySpec extends AbstractDependencyResolutionTest {

    @Shared
    String pluginA = "org.example.pluginA"
    @Shared
    String pluginAB = "org.example.pluginAB"

    MavenFileRepository repoA
    MavenFileRepository repoB


    def setup() {
        repoA = mavenRepo("repoA")
        repoB = mavenRepo("repoB")

        publishPlugin(pluginA, repoA)
        publishPlugin(pluginAB, repoA)
        publishPlugin(pluginAB, repoB)
    }

    private def publishPlugin(String pluginId, MavenFileRepository mavenRepository) {
        def pluginBuilder = new PluginBuilder(testDirectory.file(pluginId + mavenRepository.hashCode()))
        def idSegments = Splitter.on('.').split(pluginId);

        // The module which holds the plugin implementation.
        def module = mavenRepository.module(idSegments.dropRight(1).join('.'), idSegments.last(), "1.0")
        def artifactFile = module.artifact([:]).artifactFile
        module.publish()

        // The marker module which depends on the plugin implementation module.
        def marker = mavenRepository.module(pluginId, pluginId, "1.1")
        marker.dependsOn(module)
        marker.publish()

        def message = "from ${idSegments.last()} fetched from ${mavenRepository.uri}"
        def taskName = idSegments.last()
        pluginBuilder.addPluginWithPrintlnTask(taskName, message, pluginId, idSegments.last().capitalize())
        pluginBuilder.publishTo(executer, artifactFile)
    }

    private def use(MavenFileRepository... repositories) {
        settingsFile << """
            pluginRepositories {
                ${repositories.collect { "maven {url '${it.uri}' }" }.join('\n')}
            }
        """
    }

    def "Repositories are queried in declaration order"() {
        given:
        buildScript """
          plugins {
              id "$pluginAB" version "1.1"
          }
        """

        when:
        use(repoA, repoB)

        then:
        succeeds("pluginAB")
        output.contains("fetched from $repoA.uri")
    }

    def "Tries next repository if first didn't match"() {
        given:
        buildScript """
          plugins {
              id "$pluginA" version "1.1"
          }
        """

        when:
        use(repoB, repoA)

        then:
        succeeds("pluginA")
        output.contains("fetched from $repoA.uri")
    }

    def "Order of plugin requests does not affect order of artifact repositories queried"() {
        given:
        buildScript """
          plugins {
              id "$pluginA" version "1.1"
              id "$pluginAB" version "1.1"
          }
        """

        when:
        use(repoB, repoA)

        then:
        succeeds("pluginAB")
        output.contains("fetched from $repoB.uri")
    }

    def "Resolution failures are reported in declaration order"() {
        given:
        buildScript """
          plugins {
              id "org.example.foo" version "1.1"
          }
        """

        and:
        use(repoA, repoB)

        when:
        fails("pluginTask")

        then:
        failure.assertHasDescription("""Plugin [id: 'org.example.foo', version: '1.1'] was not found in any of the following sources:

- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
- maven (Could not resolve plugin artifact 'org.example.foo:org.example.foo:1.1')
- maven2 (Could not resolve plugin artifact 'org.example.foo:org.example.foo:1.1')
- Gradle Central Plugin Repository (no 'org.example.foo' plugin available - see https://plugins.gradle.org for available plugins)"""
        )
    }

    @Requires(TestPrecondition.ONLINE)
    def "Falls through to Plugin Portal if not found in any custom repository"() {
        given:
        requireOwnGradleUserHomeDir()
        buildScript """
            plugins {
                id "org.gradle.hello-world" version "0.2"
            }
        """

        when:
        use(repoA, repoB)

        then:
        succeeds("helloWorld")
    }
}
