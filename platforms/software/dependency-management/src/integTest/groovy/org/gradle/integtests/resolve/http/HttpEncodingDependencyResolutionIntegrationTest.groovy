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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class HttpEncodingDependencyResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def "handles gzip encoded content"() {
        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    ivy { url = "${server.uri}/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars {
    def files = configurations.compile
    doLast {
        assert files*.name == ['projectA-1.2.jar']
    }
}
"""

        when:
        server.expectGetGZipped('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGetGZipped('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds('listJars')
    }
}
