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
package org.gradle.integtests.resolve.artifactreuse

import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.IvyRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.gradle.util.SetSystemProperties
import org.junit.Rule

class ForceResolveIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final HttpServer server = new HttpServer()
    @Rule
    public SetSystemProperties systemProperties = new SetSystemProperties()

    def "setup"() {
        requireOwnUserHomeDir()
    }

    public void "does not use cache when force-resolve flag is set"() {
        server.start()
        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""
        when:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds 'listJars'

        when:
        server.resetExpectations()
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml.sha1', module.sha1File(module.ivyFile))
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar.sha1', module.sha1File(module.jarFile))

        then:
        executer.withArguments('--force-resolve')
        succeeds 'listJars'
    }


    IvyRepository ivyRepo() {
        return new IvyRepository(file('ivy-repo'))
    }
}
