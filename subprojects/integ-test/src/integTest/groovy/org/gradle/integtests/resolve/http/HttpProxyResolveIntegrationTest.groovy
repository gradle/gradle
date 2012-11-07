/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.resolve.http

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Unroll

class HttpProxyResolveIntegrationTest extends AbstractDependencyResolutionTest {
    @Rule TestProxyServer proxyServer = new TestProxyServer(server)
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()

    public void "uses configured proxy to access remote HTTP repository"() {
        server.start()
        proxyServer.start()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://not.a.real.domain/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        when:
        executer.withArguments("-Dhttp.proxyHost=localhost", "-Dhttp.proxyPort=${proxyServer.port}")

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds('listJars')

        and:
        proxyServer.requestCount == 2
    }

    public void "uses authenticated proxy to access remote HTTP repository"() {
        server.start()
        proxyServer.start()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    ivy {
        url "http://not.a.real.domain/repo"
    }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        when:
        executer.withArguments("-Dhttp.proxyHost=localhost", "-Dhttp.proxyPort=${proxyServer.port}", "-Dhttp.nonProxyHosts=foo",
                               "-Dhttp.proxyUser=proxyUser", "-Dhttp.proxyPassword=proxyPassword")

        and:
        proxyServer.requireAuthentication('proxyUser', 'proxyPassword')

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds('listJars')

        and:
        proxyServer.requestCount == 2
    }

    @Unroll
    public void "passes target credentials to #authScheme authenticated server via proxy"() {
        server.start()
        proxyServer.start()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    ivy {
        url "http://not.a.real.domain/repo"
        credentials {
            username 'targetUser'
            password 'targetPassword'
        }
    }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        when:
        server.authenticationScheme = authScheme
        executer.withArguments("-Dhttp.proxyHost=localhost", "-Dhttp.proxyPort=${proxyServer.port}", "-Dhttp.proxyUser=proxyUser", "-Dhttp.proxyPassword=proxyPassword")

        and:
        proxyServer.requireAuthentication('proxyUser', 'proxyPassword')

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', 'targetUser', 'targetPassword', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', 'targetUser', 'targetPassword', module.jarFile)

        then:
        succeeds('listJars')

        and:
        // 1 extra request for authentication
        proxyServer.requestCount == 3

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
    }
}
