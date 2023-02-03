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


package org.gradle.integtests.resource.s3.maven

import org.gradle.integtests.resource.s3.AbstractS3DependencyResolutionTest
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.integtests.resource.s3.fixtures.MavenS3Module
import org.gradle.util.SetSystemProperties
import org.junit.Rule

class MavenS3ProxiedRepoIntegrationTest extends AbstractS3DependencyResolutionTest {

    @Rule
    TestProxyServer proxyServer = new TestProxyServer()
    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties()

    final String artifactVersion = "1.85"
    MavenS3Module module

    @Override
    def setup() {
        proxyServer.start()
        module = getMavenS3Repo().module("org.gradle", "test", artifactVersion)
    }

    def "should proxy requests using HTTP system proxy settings"() {
        setup:
        module.publish()

        buildFile << mavenAwsRepoDsl()
        buildFile << """
configurations { compile }

dependencies{
    compile 'org.gradle:test:$artifactVersion'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        and:
        module.pom.expectDownload()
        module.artifact.expectDownload()

        when:
        executer.withArguments(
                "-Dorg.gradle.s3.endpoint=${server.uri}",
                "-Dhttp.proxyHost=localhost",
                "-Dhttp.proxyPort=${proxyServer.port}",
                "-Dhttp.nonProxyHosts=foo",
                "-Dhttp.proxyUser=proxyUser",
                "-Dhttp.proxyPassword=proxyPassword"
        )

        then:
        succeeds 'retrieve'

        and:
        proxyServer.port != server.port
        proxyServer.requestCount == 2
    }

    def "should not proxy requests when HTTP system proxy settings has a nonProxyHost rule"() {
        setup:
        module.publish()

        buildFile << mavenAwsRepoDsl()
        buildFile << """
configurations { compile }

dependencies{
    compile 'org.gradle:test:$artifactVersion'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        and:
        module.pom.expectDownload()
        module.artifact.expectDownload()

        when:
        executer.withArguments(
                "-Dorg.gradle.s3.endpoint=${server.uri}",
                "-Dhttp.proxyHost=localhost",
                "-Dhttp.proxyPort=${proxyServer.port}",
                "-Dhttp.nonProxyHosts=127.0.0.1",
                "-Dhttp.proxyUser=proxyUser",
                "-Dhttp.proxyPassword=proxyPassword"
        )

        then:
        succeeds 'retrieve'

        and:
        proxyServer.port != server.port
        proxyServer.requestCount == 0
    }

    @Override
    String getRepositoryPath() {
        return '/maven/release/'
    }
}
