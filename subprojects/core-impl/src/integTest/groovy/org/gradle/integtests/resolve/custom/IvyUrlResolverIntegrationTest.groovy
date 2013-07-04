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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.junit.Rule

class IvyUrlResolverIntegrationTest extends AbstractDependencyResolutionTest {

    @Rule ProgressLoggingFixture progressLogging = new ProgressLoggingFixture(executer, temporaryFolder)

    def setup() {
        server.expectUserAgent(null) // custom resolver uses apache/ivy as user agent strings
    }

    public void "can resolve and cache dependencies from an HTTP Ivy repository"() {
        server.start()
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()

        and:
        buildFile << """
repositories {
    add(new org.apache.ivy.plugins.resolver.URLResolver()) {
        name = "repo"
        addIvyPattern("${ivyHttpRepo.ivyPattern}")
        addArtifactPattern("${ivyHttpRepo.artifactPattern}")
    }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""
        when:
        module.expectIvyHead()
        module.expectIvyGet()
        module.expectJarHead()
        module.expectJarGet()
        executer.withDeprecationChecksDisabled()

        then:
        succeeds 'listJars'

        and:
        progressLogging.downloadProgressLogged(module.ivyFileUri)
        progressLogging.downloadProgressLogged(module.jarFileUri)

        when:
        server.resetExpectations()
        executer.withDeprecationChecksDisabled()

        // No extra calls for cached dependencies
        then:
        succeeds 'listJars'
    }

    public void "honours changing patterns from custom resolver"() {
        server.start()
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2-SNAPSHOT').publish()

        and:
        buildFile << """
repositories {
    add(new org.apache.ivy.plugins.resolver.URLResolver()) {
        name = "repo"
        addIvyPattern("${ivyHttpRepo.ivyPattern}")
        addArtifactPattern("${ivyHttpRepo.artifactPattern}")
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
        module.expectIvyHead()
        module.expectIvyGet()
        module.expectJarHead()
        module.expectJarGet()
        executer.withDeprecationChecksDisabled()

        run 'retrieve'

        then:
        def jarFile = file('libs/projectA-1.2-SNAPSHOT.jar')
        jarFile.assertIsCopyOf(module.jarFile)
        def snapshot = jarFile.snapshot()

        when:
        module.publishWithChangedContent()

        server.resetExpectations()
        // Server will be hit to get updated versions
        module.expectIvyHead()
        module.expectIvyGet()
        module.expectJarHead()
        module.expectJarGet()
        executer.withDeprecationChecksDisabled()

        run 'retrieve'

        then:
        def changedJarFile = file('libs/projectA-1.2-SNAPSHOT.jar')
        changedJarFile.assertHasChangedSince(snapshot)
        changedJarFile.assertIsCopyOf(module.jarFile)
    }
}
