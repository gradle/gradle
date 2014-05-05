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

package org.gradle.integtests.resolve.portal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.GradleVersion

import org.junit.Rule;

public class PluginPortalClientIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources resources = new TestResources(temporaryFolder)
    @Rule HttpServer server

    def setup() {
        server.start()
    }

    def "plugin declared in plugins {} block gets resolved from portal and applied"() {
        // communicates test portal hostname/port to PluginPortalResolver
        def pluginVersion = "test_localhost_${server.port}_1.0"

        TestFile metadataFile = generatePortalResponse(pluginVersion)
        File pluginFile = generatePluginJar()
        TestFile pomFile = generatePluginPom()

        server.expectGet("/api/gradle/${GradleVersion.current().version}/plugin/use/myplugin/1.0", metadataFile)
        server.expectHead("/my/plugin/1.0/plugin-1.0.pom", pomFile)
        server.expectGet("/my/plugin/1.0/plugin-1.0.pom", pomFile)
        server.expectGetMissing("/my/plugin/1.0/plugin-1.0.pom.sha1")
        server.expectHead("/my/plugin/1.0/plugin-1.0.jar", pluginFile)
        server.expectGet("/my/plugin/1.0/plugin-1.0.jar", pluginFile)
        server.expectGetMissing("/my/plugin/1.0/plugin-1.0.jar.sha1")

        buildScript """
plugins {
  id "myplugin" version "$pluginVersion"
}

task verify << {
  assert pluginApplied
}
"""

        expect:
        succeeds("verify")
    }

    private TestFile generatePortalResponse(String pluginVersion) {
        def metadataFile = file("metadata.json")
        metadataFile.text = """
{
    "id": "myplugin",
    "version": "$pluginVersion",
    "implementation": {"gav": "my:plugin:1.0", "repo": "$server.address"},
    "implementationType": "M2_JAR"
}
"""
        metadataFile
    }

    private TestFile generatePluginJar() {
        run("jar")
        def pluginFile = file("build/libs/plugin-1.0.jar")
        assert pluginFile.exists()
        pluginFile
    }

    private TestFile generatePluginPom() {
        def pomFile = file("pom.xml")
        pomFile.text = """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>my</groupId>
  <artifactId>plugin</artifactId>
  <packaging>jar</packaging>
  <version>1.0</version>
  <name>My Plugin</name>
</project>
"""
        pomFile
    }
}
