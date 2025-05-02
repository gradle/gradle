/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class IsolatedProjectsCompositeBuildParallelConfigurationIntegrationTest extends AbstractIsolatedProjectsIntegrationTest implements CompositeBuildFixture {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer(5_000)

    def setup() {
        server.start()
    }

    def "plugin builds are being configured in defined order despite plugins from them were requested concurrently"() {
        given:
        server.expect("configure-build-plugins-a")
        server.expect("configure-build-plugins-b")

        includedBuild("plugins-a") {
            applyPlugins(buildScript, "groovy-gradle-plugin")
            srcMainGroovy.file("plugin-a.gradle") << ""
            callServer(buildScript, "configure-build-plugins-a")
        }

        includedBuild("plugins-b") {
            applyPlugins(buildScript, "groovy-gradle-plugin")
            srcMainGroovy.file("plugin-b.gradle") << ""
            callServer(buildScript, "configure-build-plugins-b")
        }

        includePluginBuild(settingsFile, "plugins-a", "plugins-b")
        settingsFile << """
                include(":a")
                include(":b")
        """

        applyPlugins(file("a/build.gradle"), "plugin-a")
        applyPlugins(file("b/build.gradle"), "plugin-b")

        expect:
        isolatedProjectsRun("help")
    }

    private void callServer(TestFile from, String expression) {
        from << """
            ${server.callFromBuildUsingExpression("'$expression'")}
        """
    }
}
