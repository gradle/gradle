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

package org.gradle.plugin.use.resolve.service

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.junit.Rule

@TargetVersions(["2.1+"])
@LeaksFileHandles
class PluginResolutionCachingCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {

    public static final String PLUGIN_ID = "org.my.myplugin"
    public static final String VERSION = "1.0"
    public static final String GROUP = "my"
    public static final String ARTIFACT = "plugin"

    @Rule
    PluginResolutionServiceTestServer service = new PluginResolutionServiceTestServer(version(current), mavenRepo)
    private MavenHttpModule module = service.m2repo.module(GROUP, ARTIFACT, VERSION)

    void setup() {
        requireOwnGradleUserHomeDir()
    }

    def "cached resolution by previous version is not used by this version"() {
        when:
        def gradleUserHome = file("gradle-home")
        def currentExecuter = service.configure(version(current)).withGradleUserHomeDir(gradleUserHome)
        def previousExecuter = service.configure(version(previous)).withGradleUserHomeDir(gradleUserHome)

        def pluginBuilder = new PluginBuilder(file("plugin"))
        pluginBuilder.addPlugin("project.ext.pluginApplied = true", PLUGIN_ID)
        pluginBuilder.publishTo(currentExecuter, module.artifactFile)
        service.start()
        file("build.gradle") << """
            plugins { id '$PLUGIN_ID' version '$VERSION' }
            task pluginApplied {
                doLast {
                    assert project.pluginApplied
                }
            }
        """

        service.expectPluginQuery(PLUGIN_ID, VERSION, GROUP, ARTIFACT, VERSION)
        service.forVersion(previousExecuter.distribution.version) {
            expectPluginQuery(PLUGIN_ID, VERSION, GROUP, ARTIFACT, VERSION)
        }

        module.allowAll()

        then:
        previousExecuter.withTasks("pluginApplied").run()
        currentExecuter.withTasks("pluginApplied").run()
    }

}
