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

package org.gradle.plugin.repository

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.Repository
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@LeaksFileHandles
class ResolvingFromSingleCustomPluginRepositorySpec extends AbstractDependencyResolutionTest {
    private static final String MAVEN = 'maven'
    private static final String IVY = 'ivy'

    private enum PathType {
        ABSOLUTE, RELATIVE
    }

    private Repository repo

    private publishTestPlugin(String repoType) {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        def message = "from plugin"
        def taskName = "pluginTask"

        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "org.example.plugin")

        if (repoType == IVY) {
            repo = ivyRepo
        } else if (repoType == MAVEN) {
            repo = mavenRepo
        }
        pluginBuilder.publishAs("org.example.plugin:plugin:1.0", repo, executer)
    }

    private String useCustomRepository(PathType pathType) {
        settingsFile << """
          pluginManagement {
            repositories {
              ${repo instanceof MavenRepository ? "maven" : "ivy"} {
                  url "${PathType.ABSOLUTE.equals(pathType) ? repo.uri : repo.rootDir.name}"
              }
            }
          }
        """
        repo.uri
    }

    def "can resolve plugin from #pathType #repoType repo"() {
        given:
        publishTestPlugin(repoType)
        buildScript """
          plugins {
              id "org.example.plugin" version "1.0"
          }
        """

        and:
        useCustomRepository(pathType)

        when:
        succeeds("pluginTask")

        then:
        output.contains("from plugin")

        where:
        repoType | pathType
        IVY      | PathType.ABSOLUTE
        IVY      | PathType.RELATIVE
        MAVEN    | PathType.ABSOLUTE
        MAVEN    | PathType.RELATIVE
    }

    def "can access classes from plugin from #repoType repo"() {
        given:
        publishTestPlugin(repoType)
        buildScript """
          plugins {
              id "org.example.plugin" version "1.0"
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository(PathType.ABSOLUTE)

        when:
        succeeds("pluginTask")

        then:
        output.contains("I'm here")

        where:
        repoType << [IVY, MAVEN]
    }

    def "can apply plugin from #repoType repo to subprojects"() {
        given:
        publishTestPlugin(repoType)
        buildScript """
          plugins {
              id "org.example.plugin" version "1.0" apply false
          }

          subprojects {
            apply plugin: 'org.example.plugin'
          }
        """

        and:
        useCustomRepository(PathType.ABSOLUTE)
        settingsFile << """
            include 'sub'
        """

        expect:
        succeeds("sub:pluginTask")

        where:
        repoType << [IVY, MAVEN]
    }

    def "custom #repoType repo is not mentioned in plugin resolution errors if none is defined"() {
        given:
        publishTestPlugin(repoType)
        buildScript """
          plugins {
              id "org.example.plugin"
          }
        """

        when:
        fails("pluginTask")

        then:
        failure.assertHasNoCause(repoType)

        where:
        repoType << [IVY, MAVEN]
    }

    @Requires(UnitTestPreconditions.Online)
    def "Fails gracefully if a plugin is not found in #repoType repo"() {
        given:
        publishTestPlugin(repoType)
        buildScript """
          plugins {
              id "org.example.foo" version "1.1"
          }
        """

        and:
        def repoUrl = useCustomRepository(PathType.ABSOLUTE)

        when:
        fails("pluginTask")

        then:
        failure.assertHasDescription("""
            Plugin [id: 'org.example.foo', version: '1.1'] was not found in any of the following sources:

            - Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            - Plugin Repositories (could not resolve plugin artifact 'org.example.foo:org.example.foo.gradle.plugin:1.1')
              Searched in the following repositories:
                ${repoType}($repoUrl)
        """.stripIndent().trim())

        where:
        repoType << [IVY, MAVEN]
    }

    def "Works with subprojects and relative #repoType repo specification."() {
        given:
        publishTestPlugin(repoType)
        def subprojectScript = file("subproject/build.gradle")
        subprojectScript << """
          plugins {
              id "org.example.plugin" version "1.0"
          }
        """

        and:
        useCustomRepository(PathType.RELATIVE)

        and:
        settingsFile << """
          include 'subproject'
        """

        expect:
        succeeds("subproject:pluginTask")

        where:
        repoType << [IVY, MAVEN]
    }

    @NotYetImplemented
    def "Can specify repo in init script."() {
        given:
        publishTestPlugin(MAVEN)
        buildScript """
           plugins {
             id "org.example.plugin" version "1.0"
           }
        """

        and:
        def initScript = file('definePluginRepo.gradle')
        initScript << """
          pluginManagement {
            repositories {
                maven {
                  url "${mavenRepo.uri}"
                }
            }
          }
        """
        args('-I', initScript.absolutePath)

        when:
        succeeds('pluginTask')

        then:
        output.contains('from plugin')
    }

    def "can resolve plugins even if buildscript block contains wrong repo with same name"() {
        given:
        publishTestPlugin(MAVEN)
        buildScript """
          buildscript {
            repositories {
                maven {
                    url '${new MavenFileRepository(file("other-repo")).uri}'
                }
            }
          }
          plugins {
              id "org.example.plugin" version "1.0"
          }
        """

        and:
        useCustomRepository(PathType.ABSOLUTE)

        when:
        succeeds("pluginTask")

        then:
        output.contains("from plugin")
    }

    def "Does not fall through to Plugin Portal if custom repo is defined"() {
        given:
        publishTestPlugin(MAVEN)
        buildScript """
            plugins {
                id "org.gradle.hello-world" version "0.2" //this exists in the plugin portal
            }
        """

        and:
        useCustomRepository(PathType.ABSOLUTE)

        expect:
        fails("helloWorld")
        failure.assertHasDescription("Plugin [id: 'org.gradle.hello-world', version: '0.2'] was not found in any of the following sources:")
    }

    def "verify plugin portal is not used when defining pluginManagement repos in settings plugin"() {
        settingsFile << """
            pluginManagement {
                includeBuild("settings-script")
            }

            plugins {
                id 'settings-script'
            }

            pluginManagement.repositories.each { println it.url }
        """

        buildFile << """
            plugins {
                id "org.gradle.hello-world" version "0.2" //this exists in the plugin portal
            }
        """

        def pluginRoot = file("settings-script")

        pluginRoot.file("settings.gradle") << """
            rootProject.name = "settings-script"
        """
        pluginRoot.file("build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
        pluginRoot.file("src/main/groovy/settings-script.settings.gradle") << """
            pluginManagement {
                repositories {
                    maven {
                        url '${mavenRepo.uri}'
                    }
                }
            }
        """

        expect:
        fails("helloWorld")
    }
}
