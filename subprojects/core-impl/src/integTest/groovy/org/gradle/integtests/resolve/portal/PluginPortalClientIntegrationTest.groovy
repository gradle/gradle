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
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.util.GradleVersion
import org.junit.Rule

public class PluginPortalClientIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    TestResources resources = new TestResources(temporaryFolder)

    @Rule
    HttpServer server = new HttpServer()

    MavenHttpRepository mavenHttpRepo = new MavenHttpRepository(server, mavenRepo)

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

        expect:
        fails("verify")
        failure.assertHasCause("Cannot resolve plugin request [plugin: 'myplugin', version: '$pluginVersion'] from plugin repositories")
    }

    def "plugin can have dynamic module version"() {
        def pluginVersion = "test_localhost_${server.port}_1.0"

        servePlugin("myplugin", "1.0", "my", "plugin", "2.+", "2.2")

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

    def "portal JSON response with unknown implementation type"() {
        def pluginVersion = "test_localhost_${server.port}_1.0"

        def metaDataFile = generateCustomPluginMetaData """
{
    "id": "myplugin",
    "version": "1.0",
    "implementation": {"gav": "my:plugin:1.0", "repo": "$server.address/repo"},
    "implementationType": "SUPER_GREAT"
}
"""
        server.expectGet("/api/gradle/${GradleVersion.current().version}/plugin/use/myplugin/1.0", metaDataFile)

        buildScript """
plugins {
    id "myplugin" version "$pluginVersion"
}

task verify
"""

        expect:
        fails("verify")
        //failure.assertHasCause("Cannot resolve plugin request [plugin: 'myplugin', version: '$pluginVersion'] from plugin repositories")
    }

    def "portal JSON response with missing repo"() {
        def pluginVersion = "test_localhost_${server.port}_1.0"

        def metaDataFile = generateCustomPluginMetaData """
{
    "id": "myplugin",
    "version": "1.0",
    "implementation": {"gav": "my:plugin:1.0"},
    "implementationType": "M2_JAR"
}
"""
        server.expectGet("/api/gradle/${GradleVersion.current().version}/plugin/use/myplugin/1.0", metaDataFile)

        buildScript """
plugins {
    id "myplugin" version "$pluginVersion"
}

task verify
"""

        expect:
        fails("verify")
        //failure.assertHasCause("Cannot resolve plugin request [plugin: 'myplugin', version: '$pluginVersion'] from plugin repositories")
    }

    def "portal JSON response with invalid JSON syntax"() {
        def pluginVersion = "test_localhost_${server.port}_1.0"

        def metaDataFile = generateCustomPluginMetaData "{[}]"

        server.expectGet("/api/gradle/${GradleVersion.current().version}/plugin/use/myplugin/1.0", metaDataFile)

        buildScript """
plugins {
    id "myplugin" version "$pluginVersion"
}

task verify
"""

        expect:
        fails("verify")
        //failure.assertHasCause("Cannot resolve plugin request [plugin: 'myplugin', version: '$pluginVersion'] from plugin repositories")
    }

    private TestFile generateCustomPluginMetaData(String metaData) {
        def metaDataFile = file("metadata.json")
        metaDataFile.text = metaData
        metaDataFile
    }

    private TestFile generatePluginMetaData(String version) {
        def metaDataFile = file("metadata.json")
        metaDataFile.text = """
{
    "id": "myplugin",
    "version": "1.0",
    "implementation": {"gav": "my:plugin:$version", "repo": "$server.address/repo"},
    "implementationType": "M2_JAR"
}
"""
        metaDataFile
    }

    private TestFile generatePluginJar() {
        run("jar")
        def pluginFile = file("build/libs/plugin-1.0.jar")
        assert pluginFile.exists()
        pluginFile
    }

    private void servePlugin(String pluginId, String pluginVersion, String group,
                             String artifact, String version, String resolvedVersion = version) {
        def metaDataFile = generatePluginMetaData(version)
        server.expectGet("/api/gradle/${GradleVersion.current().version}/plugin/use/$pluginId/$pluginVersion", metaDataFile)

        def module = mavenHttpRepo.module(group, artifact, resolvedVersion).publish()
        generatePluginJar().copyTo(module.artifactFile)

        module.allowAll()
        mavenHttpRepo.getModuleMetaData(group,artifact).allowGetOrHead()
    }
}
