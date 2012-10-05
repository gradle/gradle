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
package org.gradle.integtests.resolve.custom

import org.gradle.integtests.fixtures.ProgressLoggingFixture
import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.junit.Rule

class IvyUrlResolverIntegrationTest extends AbstractDependencyResolutionTest {

    @Rule ProgressLoggingFixture progressLogging

    def setup() {
        server.expectUserAgent(null) // custom resolver uses apache/ivy as useragent strings
    }

    public void "can resolve and cache dependencies from an HTTP Ivy repository"() {
        server.start()
        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    add(new org.apache.ivy.plugins.resolver.URLResolver()) {
        name = "repo"
        addIvyPattern("http://localhost:${server.port}/repo/[organization]/ivy-[module]-[revision].xml")
        addArtifactPattern("http://localhost:${server.port}/repo/[organization]/[module]-[revision].[ext]")
    }
}
configurations { compile }
configurations.compile.resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""
        when:
        server.expectHead('/repo/group/ivy-projectA-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/ivy-projectA-1.2.xml', module.ivyFile)
        server.expectHead('/repo/group/projectA-1.2.jar', module.jarFile)
        server.expectGet('/repo/group/projectA-1.2.jar', module.jarFile)

        and:
        progressLogging.withProgressLogging(getExecuter(), module.jarFile)
        then:
        succeeds 'listJars'
        and:
        progressLogging.downloadProgressLogged("http://localhost:${server.port}/repo/group/projectA-1.2.jar")
        when:
        server.resetExpectations()
        progressLogging.resetExpectations()
        // No extra calls for cached dependencies

        then:
        succeeds 'listJars'
        progressLogging.noProgressLogged()
    }

    public void "honours changing patterns from custom resolver"() {
        server.start()
        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2-SNAPSHOT')
        module.publish()

        and:
        buildFile << """
repositories {
    add(new org.apache.ivy.plugins.resolver.URLResolver()) {
        name = "repo"
        addIvyPattern("http://localhost:${server.port}/repo/[organization]/ivy-[module]-[revision].xml")
        addArtifactPattern("http://localhost:${server.port}/repo/[organization]/[module]-[revision].[ext]")
        changingMatcher = 'regexp'
        changingPattern = '.*SNAPSHOT.*'
    }
}
configurations { compile }
configurations.compile.resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
dependencies { compile 'group:projectA:1.2-SNAPSHOT' }
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        when:
        server.expectHead('/repo/group/ivy-projectA-1.2-SNAPSHOT.xml', module.ivyFile)
        server.expectGet('/repo/group/ivy-projectA-1.2-SNAPSHOT.xml', module.ivyFile)
        server.expectHead('/repo/group/projectA-1.2-SNAPSHOT.jar', module.jarFile)
        server.expectGet('/repo/group/projectA-1.2-SNAPSHOT.jar', module.jarFile)

        run 'retrieve'

        then:
        def jarFile = file('libs/projectA-1.2-SNAPSHOT.jar')
        jarFile.assertIsCopyOf(module.jarFile)
        def snapshot = jarFile.snapshot()

        when:
        module.publishWithChangedContent()

        server.resetExpectations()
        // Server will be hit to get updated versions
        server.expectHead('/repo/group/ivy-projectA-1.2-SNAPSHOT.xml', module.ivyFile)
        server.expectGet('/repo/group/ivy-projectA-1.2-SNAPSHOT.xml', module.ivyFile)
        server.expectHead('/repo/group/projectA-1.2-SNAPSHOT.jar', module.jarFile)
        server.expectGet('/repo/group/projectA-1.2-SNAPSHOT.jar', module.jarFile)

        run 'retrieve'

        then:
        def changedJarFile = file('libs/projectA-1.2-SNAPSHOT.jar')
        changedJarFile.assertHasChangedSince(snapshot)
        changedJarFile.assertIsCopyOf(module.jarFile)
    }
}
