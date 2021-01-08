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
package org.gradle.plugin.use

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

class VersionedPluginUseIntegrationTest extends AbstractIntegrationSpec {

    public static final String PLUGIN_ID = "org.myplugin"
    public static final String GROUP = "my"
    public static final String ARTIFACT = "plugin"

    def pluginBuilder = new PluginBuilder(file(ARTIFACT))

    @Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def setup() {
        /*

        https://github.com/gradle/build-tool-flaky-tests/issues/49

        The plugin is published to a repository inside the test folder, which is accessed via a localhost address (using MavenHttpPluginRepository).
        When we resolve, we cache the results in the shared home folder. Now if another test runs, and the repository ends up looking the same (same localhost address).
        The wrong cached artifact is used.
        That's why these kind of resolution tests need requireOwnGradleUserHomeDir().

         */
        executer.requireOwnGradleUserHomeDir()
        publishPlugin("1.0")
        publishPlugin("2.0")
    }

    def "can specify plugin version"() {
        when:
        buildScript "plugins { id '$PLUGIN_ID' version '1.0' }"

        then:
        verifyPluginApplied('1.0')
    }

    def "can specify plugin version using gradle properties"() {
        when:
        file("gradle.properties") << "myPluginVersion=2.0"
        buildScript """
            plugins {
                id '$PLUGIN_ID' version "\${myPluginVersion}"
            }
"""

        then:
        verifyPluginApplied('2.0')
    }

    def "can specify plugin version using command-line project property"() {
        when:
        buildScript """
            plugins {
                id '$PLUGIN_ID' version "\${myPluginVersion}"
            }
"""

        args("-PmyPluginVersion=2.0")
        then:

        verifyPluginApplied('2.0')
    }

    def "can specify plugin version using buildSrc"() {
        when:
        file("buildSrc/src/main/java/MyVersions.java") << """
            public class MyVersions {
                public static final String MY_PLUGIN_VERSION = "2.0";
            }
"""
        buildScript """
            import static MyVersions.*
            plugins {
                id '$PLUGIN_ID' version "\${MY_PLUGIN_VERSION}"
            }
"""
        then:

        verifyPluginApplied('2.0')
    }

    def "can specify plugin version using buildSrc constant"() {
        when:
        file("buildSrc/src/main/java/MyVersions.java") << """
            public class MyVersions {
                public static final String MY_PLUGIN_VERSION = "2.0";
            }
"""
        buildScript """
            import static MyVersions.*
            plugins {
                id '$PLUGIN_ID' version "\${MY_PLUGIN_VERSION}"
            }
"""
        then:
        verifyPluginApplied('2.0')
    }

    def "can use different plugin versions in sibling projects"() {
        when:
        settingsFile << "include 'p1', 'p2'"

        file("p1/build.gradle") << """
            plugins {
                id '$PLUGIN_ID' version '1.0'
            }
            ${verifyPluginTask('1.0')}
"""
        file("p2/build.gradle") << """
            plugins {
                id '$PLUGIN_ID' version '2.0'
            }
            ${verifyPluginTask('2.0')}
"""

        then:
        succeeds "verify"
    }

    def verifyPluginApplied(String version) {
        buildFile << verifyPluginTask(version)
        succeeds "verify"
    }

    def verifyPluginTask(String version) {
        """
            tasks.register("verify") {
                def pluginVersion = project.ext.pluginVersion
                doLast {
                    assert pluginVersion == "$version"
                }
            }
"""
    }

    void publishPlugin(String version) {
        publishPlugin("project.ext.pluginVersion = '$version'", version)
    }

    void publishPlugin(String impl, String version) {
        pluginBuilder.addPlugin(impl, PLUGIN_ID, "TestPlugin${version.replace('.', '_')}")
        pluginBuilder.publishAs(GROUP, ARTIFACT, version, pluginRepo, executer).allowAll()
    }
}
