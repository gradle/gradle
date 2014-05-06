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

import org.gradle.api.plugins.UnknownPluginException
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

        servePlugin("myplugin", "1.0", "my", "plugin", "1.0")

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

    def "404 response from plugin portal fails resolution"() {
        def pluginVersion = "test_localhost_${server.port}_1.0"
        server.expectGetMissing("/api/gradle/${GradleVersion.current().version}/plugin/use/myplugin/1.0")

        buildScript """
plugins {
    id "myplugin" version "$pluginVersion"
}

task verify
"""

        when:
        run("verify")

        then:
        def e = thrown(Exception)
        def cause = getCause(e, UnknownPluginException)
        cause.message.contains "[plugin: 'myplugin', version: '$pluginVersion']"
    }

    private TestFile generatePluginMetaData(String pluginVersion) {
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

    private Throwable getCause(Throwable throwable, Class<? extends Throwable> type) {
        while (throwable != null) {
            if (type.isInstance(throwable)) { return throwable }
            println throwable
            throwable = throwable.cause
        }
        assert false, "No cause of type $type.name found for exception: $throwable"
    }

    private void servePlugin(String pluginId, String pluginVersion, String group, String artifact, String version) {
        def metaDataFile = generatePluginMetaData(pluginVersion)
        def pluginFile = generatePluginJar()
        def pomFile = generatePluginPom()

        server.expectGet("/api/gradle/${GradleVersion.current().version}/plugin/use/$pluginId/$pluginVersion", metaDataFile)
        def modulePath = "/$group/$artifact/$version/$artifact-$version"
        server.allowHead("${modulePath}.pom", pomFile)
        server.allowGetMissing("${modulePath}.pom.sha1")
        server.expectGet("${modulePath}.pom", pomFile)
        server.allowHead("${modulePath}.jar", pluginFile)
        server.allowGetMissing("${modulePath}.jar.sha1")
        server.expectGet("${modulePath}.jar", pluginFile)
    }
}
